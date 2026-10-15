package com.atlas.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

/**
 * Correlation id e métricas. {@code @AutoConfigureObservability} liga tracing e Prometheus, que os outros
 * testes de Spring Boot deixam desligados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
@Testcontainers
@TestPropertySource(properties = {
        "app.security.jwt.secret=test-secret-with-at-least-32-bytes-0123456789",
        "app.booking.expiration-job-enabled=false",
        "app.rate-limit.rules.login.capacity=1"})
class ObservabilityTest {

    private static final String TRACE_ID = "[0-9a-f]{32}";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void everyResponseCarriesTheTraceIdEvenWhenSecurityRejectsIt() throws Exception {
        mvc.perform(get("/airports").with(user(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", org.hamcrest.Matchers.matchesPattern(TRACE_ID)));

        // 401 sai do filtro do Spring Security, antes do MVC
        mvc.perform(get("/bookings"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Trace-Id", org.hamcrest.Matchers.matchesPattern(TRACE_ID)));
    }

    @Test
    void errorsCarryTheSameTraceIdAsTheHeader() throws Exception {
        MvcResult result = mvc.perform(get("/bookings/{id}", UUID.randomUUID()).with(user(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andReturn();

        String header = result.getResponse().getHeader("X-Trace-Id");
        assertThat((String) JsonPath.read(result.getResponse().getContentAsString(), "$.traceId"))
                .isEqualTo(header).matches(TRACE_ID);
    }

    @Test
    void prometheusExposesBusinessMetrics() throws Exception {
        UUID me = UUID.randomUUID();
        // um hold (evento de negócio), um 429 (rate limit) e um turno com o assistente sem chave (503)
        hold(me);
        login();
        login();
        String conversation = JsonPath.read(mvc.perform(post("/assistant/conversations").with(user(me)))
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/assistant/conversations/{id}/messages", conversation).with(user(me))
                        .header("Idempotency-Key", "k").contentType(APPLICATION_JSON).content("{\"content\":\"oi\"}"))
                .andExpect(status().isServiceUnavailable());

        String metrics = mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(metrics)
                .containsPattern("atlas_business_events_total\\{[^}]*action=\"BOOKING_HELD\"[^}]*} 1\\.0")
                .containsPattern("atlas_ratelimit_rejections_total\\{[^}]*rule=\"login\"[^}]*} 1\\.0")
                .containsPattern("atlas_assistant_turns_total\\{[^}]*outcome=\"unavailable\"[^}]*} 1\\.0")
                .contains("application=\"atlas-flight-booking-service\"");
    }

    private void login() throws Exception {
        mvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "x@example.com", "password", "qualquer-senha-1"))));
    }

    private void hold(UUID owner) throws Exception {
        jdbc.update("INSERT INTO airports (code, name, city, country_code, time_zone) VALUES "
                + "('GRU', 'Guarulhos', 'São Paulo', 'BR', 'America/Sao_Paulo'), "
                + "('GIG', 'Galeão', 'Rio de Janeiro', 'BR', 'America/Sao_Paulo') ON CONFLICT DO NOTHING");
        UUID flight = UUID.randomUUID();
        Instant departure = Instant.now().plusSeconds(86_400 * 10);
        jdbc.update("INSERT INTO flights (id, flight_number, origin_code, destination_code, departure_at, arrival_at) "
                + "VALUES (?, 'AT900', 'GRU', 'GIG', ?, ?)", flight, departure.atOffset(ZoneOffset.UTC),
                departure.plusSeconds(3900).atOffset(ZoneOffset.UTC));
        jdbc.update("INSERT INTO seat_inventory (id, flight_id, fare_class_code, total_seats, available_seats, "
                + "price_amount, currency) VALUES (?, ?, 'ECONOMY_LIGHT', 10, 10, ?, 'BRL')",
                UUID.randomUUID(), flight, new BigDecimal("399.90"));
        mvc.perform(post("/bookings").with(user(owner)).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("flightId", flight, "fareClass", "ECONOMY_LIGHT",
                                "passengers", List.of(Map.of("fullName", "Ana Teste", "documentNumber", "X1",
                                        "birthDate", "1990-01-01"))))))
                .andExpect(status().isCreated());
    }

    private static RequestPostProcessor user(UUID id) {
        return jwt().jwt(token -> token.subject(id.toString()));
    }
}
