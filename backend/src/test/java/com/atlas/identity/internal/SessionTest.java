package com.atlas.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.atlas.shared.MutableClock;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/**
 * Sessão com access token curto e refresh token rotacionado em cookie. O relógio controlado vale também para
 * a validação do JWT, então dá para ver o access token vencer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "app.security.jwt.secret=test-secret-with-at-least-32-bytes-0123456789",
        "app.rate-limit.enabled=false",
        "app.booking.expiration-job-enabled=false"})
class SessionTest {

    static final String COOKIE = "atlas_refresh";
    static final String PASSWORD = "senha-forte-123";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @TestConfiguration
    static class Clocks {
        @Bean
        @Primary
        MutableClock mutableClock() {
            // segundo cheio: o Postgres arredonda para microssegundos, e um instante com nanos poderia cair
            // do outro lado da fronteira de expiração que o teste confere
            return new MutableClock(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    @Autowired
    JwtEncoder encoder;

    @Autowired
    RefreshTokenCleanup cleanup;

    String email;

    @BeforeEach
    void user() throws Exception {
        email = "user-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isCreated());
    }

    @Test
    void loginReturnsShortAccessTokenAndRefreshCookieStoredOnlyAsHash() throws Exception {
        MvcResult login = login().andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn();

        String setCookie = login.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).startsWith(COOKIE + "=").contains("HttpOnly").contains("Secure")
                .contains("SameSite=Strict").contains("Path=/");
        String raw = refreshCookie(login).getValue();
        assertThat(jdbc.queryForList("SELECT token_hash FROM refresh_tokens", String.class))
                .contains(SessionService.hash(raw)).doesNotContain(raw);
    }

    @Test
    void refreshRotatesTheTokenAndTheNewAccessTokenWorks() throws Exception {
        Cookie first = refreshCookie(login().andReturn());

        MvcResult refreshed = refresh(first).andExpect(status().isOk()).andReturn();
        Cookie second = refreshCookie(refreshed);
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        mvc.perform(get("/me").header("Authorization", "Bearer " + accessToken(refreshed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
        refresh(second).andExpect(status().isOk());
    }

    @Test
    void twoTabsRefreshingTogetherIsARaceNotATheft() throws Exception {
        Cookie first = refreshCookie(login().andReturn());
        Cookie second = refreshCookie(refresh(first).andReturn());

        // a outra aba ainda tinha o cookie antigo: 409 e a sessão segue viva
        refresh(first).andExpect(status().isConflict()).andExpect(jsonPath("$.reason").value("REFRESH_RACE"));
        refresh(second).andExpect(status().isOk());
    }

    @Test
    void reusingAnOldTokenAfterTheGraceWindowRevokesTheWholeLogin() throws Exception {
        Cookie first = refreshCookie(login().andReturn());
        Cookie second = refreshCookie(refresh(first).andReturn());

        clock.advance(Duration.ofSeconds(11));
        refresh(first).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.reason").value("INVALID_REFRESH_TOKEN"));

        // quem estava com o token novo (talvez o ladrão) também perde a sessão
        refresh(second).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'REFRESH_TOKEN_REUSED'",
                Long.class)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void logoutEndsTheSessionAndClearsTheCookie() throws Exception {
        Cookie cookie = refreshCookie(login().andReturn());

        MvcResult logout = mvc.perform(post("/auth/logout").cookie(cookie)).andExpect(status().isNoContent()).andReturn();
        assertThat(logout.getResponse().getHeader("Set-Cookie")).contains("Max-Age=0");

        long reuseAlerts = jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'REFRESH_TOKEN_REUSED'", Long.class);
        clock.advance(Duration.ofSeconds(11));
        refresh(cookie).andExpect(status().isUnauthorized());
        // token de logout reapresentado é aba velha, não roubo: sem alerta novo
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'REFRESH_TOKEN_REUSED'", Long.class))
                .isEqualTo(reuseAlerts);

        mvc.perform(post("/auth/logout")).andExpect(status().isNoContent()); // sem cookie também responde 204
    }

    @Test
    void sessionEndsAfterSevenIdleDaysOrThirtyDaysInTotal() throws Exception {
        Cookie idle = refreshCookie(login().andReturn());
        clock.advance(Duration.ofDays(7));
        refresh(idle).andExpect(status().isUnauthorized());

        // usando a cada 6 dias a sessão anda, mas não passa de 30 dias desde o login
        Cookie active = refreshCookie(login().andReturn());
        for (int day = 6; day < 30; day += 6) {
            clock.advance(Duration.ofDays(6));
            active = refreshCookie(refresh(active).andExpect(status().isOk()).andReturn());
        }
        clock.advance(Duration.ofDays(6)); // dia 30
        refresh(active).andExpect(status().isUnauthorized());
    }

    @Test
    void endingAllSessionsRevokesEveryDevice() throws Exception {
        MvcResult laptop = login().andReturn();
        Cookie phone = refreshCookie(login().andReturn());

        mvc.perform(delete("/me/sessions").header("Authorization", "Bearer " + accessToken(laptop)))
                .andExpect(status().isNoContent());

        refresh(refreshCookie(laptop)).andExpect(status().isUnauthorized());
        refresh(phone).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'SESSIONS_REVOKED'", Long.class))
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void accessTokenExpiresAndIssuerIsChecked() throws Exception {
        String token = accessToken(login().andReturn());
        clock.advance(Duration.ofMinutes(17)); // 15 min de TTL + 60 s de tolerância do validador, com folga
        mvc.perform(get("/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());

        Instant now = clock.instant();
        String foreign = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer("outro-sistema").subject(UUID.randomUUID().toString())
                        .issuedAt(now).expiresAt(now.plusSeconds(600)).claim("role", "USER").build())).getTokenValue();
        mvc.perform(get("/me").header("Authorization", "Bearer " + foreign)).andExpect(status().isUnauthorized());
    }

    @Test
    void cleanupRemovesOnlyFamiliesDeadForAWeek() throws Exception {
        jdbc.execute("DELETE FROM refresh_tokens");
        Cookie loggedOut = refreshCookie(login().andReturn());
        mvc.perform(post("/auth/logout").cookie(loggedOut)).andExpect(status().isNoContent());
        Cookie rotated = refreshCookie(refresh(refreshCookie(login().andReturn())).andReturn()); // família de 2 tokens

        clock.advance(Duration.ofDays(6));
        Cookie active = refreshCookie(refresh(rotated).andReturn()); // em uso: a família continua viva
        assertThat(cleanup.purge()).isZero(); // a do logout morreu há só 6 dias

        clock.advance(Duration.ofDays(2));
        assertThat(cleanup.purge()).isEqualTo(1); // a do logout, 8 dias depois
        refresh(active).andExpect(status().isOk());
    }

    @Test
    void refreshWithoutCookieIsUnauthorized() throws Exception {
        mvc.perform(post("/auth/refresh")).andExpect(status().isUnauthorized());
        refresh(new Cookie(COOKIE, "inventado")).andExpect(status().isUnauthorized());
    }

    private ResultActions login() throws Exception {
        return mvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))));
    }

    private ResultActions refresh(Cookie cookie) throws Exception {
        return mvc.perform(post("/auth/refresh").cookie(cookie));
    }

    private static Cookie refreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(COOKIE);
        assertThat(cookie).as("refresh cookie").isNotNull();
        return new Cookie(COOKIE, cookie.getValue());
    }

    private static String accessToken(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }
}
