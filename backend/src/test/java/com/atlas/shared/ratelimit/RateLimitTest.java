package com.atlas.shared.ratelimit;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.atlas.shared.MutableClock;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

/** Limites pequenos para caber no teste; o relógio controlado mostra as fichas voltando. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "app.security.jwt.secret=test-secret-with-at-least-32-bytes-0123456789",
        "app.booking.expiration-job-enabled=false",
        "app.rate-limit.rules.login.capacity=3",
        "app.rate-limit.rules.register.capacity=100",
        "app.rate-limit.rules.chat-minute.capacity=2",
        "app.rate-limit.login-failures.capacity=2"})
class RateLimitTest {

    static final Instant NOW = Instant.parse("2026-03-10T12:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @TestConfiguration
    static class Clocks {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(NOW);
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    MutableClock clock;

    @BeforeEach
    void resetClock() {
        // cada teste anda o relógio para frente: buckets dos testes anteriores já estão cheios de novo
        clock.advance(Duration.ofDays(2));
    }

    @Test
    void loginIsLimitedPerIp() throws Exception {
        for (int i = 0; i < 3; i++) {
            login(unknownEmail(), "qualquer-senha-1", "203.0.113.1").andExpect(status().isUnauthorized());
        }

        login(unknownEmail(), "qualquer-senha-1", "203.0.113.1")
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("Retry-After", "20")) // 3 por minuto: uma ficha a cada 20s
                .andExpect(jsonPath("$.reason").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(20));

        // outro IP não é afetado
        login(unknownEmail(), "qualquer-senha-1", "203.0.113.2").andExpect(status().isUnauthorized());

        clock.advance(Duration.ofSeconds(20));
        login(unknownEmail(), "qualquer-senha-1", "203.0.113.1").andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordsAreLimitedPerEmailEvenFromManyIps() throws Exception {
        String email = register();
        login(email, "senha-errada-1", "198.51.100.1").andExpect(status().isUnauthorized());
        login(email, "senha-errada-2", "198.51.100.2").andExpect(status().isUnauthorized());

        // nem a senha certa entra enquanto o bloqueio durar, e de qualquer IP
        login(email, "senha-forte-123", "198.51.100.3")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        // outra conta segue normal
        login(register(), "senha-forte-123", "198.51.100.4").andExpect(status().isOk());

        clock.advance(Duration.ofMinutes(8)); // 2 em 15 min: uma ficha volta a cada 7min30
        login(email, "senha-forte-123", "198.51.100.5").andExpect(status().isOk());
    }

    @Test
    void successfulLoginsDoNotCountAsFailures() throws Exception {
        String email = register();
        for (int i = 0; i < 3; i++) {
            login(email, "senha-forte-123", "192.0.2." + i).andExpect(status().isOk());
        }
        login(email, "senha-errada-1", "192.0.2.10").andExpect(status().isUnauthorized());
        login(email, "senha-forte-123", "192.0.2.11").andExpect(status().isOk());
    }

    @Test
    void chatIsLimitedPerUser() throws Exception {
        UUID me = UUID.randomUUID();
        String conversation = conversation(me);

        // sem chave da OpenAI o chat responde 503, mas a tentativa conta do mesmo jeito
        say(me, conversation).andExpect(status().isServiceUnavailable());
        say(me, conversation).andExpect(status().isServiceUnavailable());
        say(me, conversation).andExpect(status().isTooManyRequests());

        // o limite é do usuário, não do IP
        UUID other = UUID.randomUUID();
        say(other, conversation(other)).andExpect(status().isServiceUnavailable());
    }

    private ResultActions login(String email, String password, String ip) throws Exception {
        return mvc.perform(post("/auth/login").with(from(ip)).contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private String register() throws Exception {
        String email = unknownEmail();
        mvc.perform(post("/auth/register").with(from("10.9.9.9")).contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "senha-forte-123"))))
                .andExpect(status().isCreated());
        return email;
    }

    private String conversation(UUID user) throws Exception {
        return JsonPath.read(mvc.perform(post("/assistant/conversations").with(user(user)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private ResultActions say(UUID user, String conversation) throws Exception {
        return mvc.perform(post("/assistant/conversations/{id}/messages", conversation).with(user(user))
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(APPLICATION_JSON)
                .content("{\"content\":\"oi\"}"));
    }

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private static RequestPostProcessor user(UUID id) {
        return jwt().jwt(token -> token.subject(id.toString()));
    }

    private static String unknownEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
