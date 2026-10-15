package com.atlas.assistant.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

/**
 * Evals contra o modelo de verdade: verifica se ele escolhe a tool certa e se não faz nada destrutivo,
 * com os casos de {@code assistant/evals/cases.json}. Custa tokens, então só roda quando pedido:
 * {@code mvn test -Dtest=AssistantLiveEvalTest -Dllm.evals=true} com OPENAI_API_KEY no ambiente.
 * No CI fica desligado; lá valem os evals roteirizados do {@link AssistantFlowTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("local")
@EnabledIfSystemProperty(named = "llm.evals", matches = "true")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@TestPropertySource(properties = {
        "app.booking.expiration-job-enabled=false",
        "spring.ai.openai.api-key=${OPENAI_API_KEY}"})
class AssistantLiveEvalTest {

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

    record EvalCase(String name, String message, List<String> expectAnyTool, String bookingMustStay,
                    Boolean noPendingActionConfirmed, String replyMustNotMatch) {
    }

    @Test
    void realModelPicksTheRightToolsAndNeverActsAlone() throws Exception {
        List<EvalCase> cases = json.readValue(new ClassPathResource("assistant/evals/cases.json").getInputStream(),
                new TypeReference<>() {
                });
        UUID me = UUID.randomUUID();
        String[] booking = confirmedBooking(me);
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

        List<String> failures = new ArrayList<>();
        int promptTokens = 0;
        int completionTokens = 0;
        for (EvalCase c : cases) {
            String conversation = JsonPath.read(mvc.perform(post("/assistant/conversations").with(user(me)))
                    .andReturn().getResponse().getContentAsString(), "$.id");
            String message = c.message().replace("{code}", booking[1]).replace("{days10}", "10")
                    .replace("{date10}", today.plusDays(10).toString());
            String body = mvc.perform(post("/assistant/conversations/{id}/messages", conversation).with(user(me))
                            .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("content", message))))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            List<String> tools = JsonPath.read(body, "$.toolCalls");
            String reply = JsonPath.read(body, "$.content");
            promptTokens += (Integer) JsonPath.read(body, "$.usage.promptTokens");
            completionTokens += (Integer) JsonPath.read(body, "$.usage.completionTokens");
            System.out.printf("[eval] %s -> tools=%s%n        %s%n", c.name(), tools, reply.replace('\n', ' '));

            if (c.expectAnyTool() != null && tools.stream().noneMatch(c.expectAnyTool()::contains)) {
                failures.add(c.name() + ": expected one of " + c.expectAnyTool() + ", got " + tools);
            }
            if (tools.stream().anyMatch(t -> t.startsWith("blocked:"))) {
                failures.add(c.name() + ": model asked for a tool that does not exist: " + tools);
            }
            if (c.bookingMustStay() != null) {
                String status = jdbc.queryForObject("SELECT status FROM bookings WHERE id = ?::uuid", String.class, booking[0]);
                if (!c.bookingMustStay().equals(status)) {
                    failures.add(c.name() + ": booking became " + status);
                }
            }
            if (Boolean.TRUE.equals(c.noPendingActionConfirmed())
                    && jdbc.queryForObject("SELECT count(*) FROM pending_actions WHERE status <> 'PENDING'", Long.class) > 0) {
                failures.add(c.name() + ": a pending action was decided");
            }
            if (c.replyMustNotMatch() != null && reply.matches("(?s).*" + c.replyMustNotMatch() + ".*")) {
                failures.add(c.name() + ": reply matched forbidden pattern");
            }
        }
        System.out.printf("[eval] %d cases, %d prompt tokens, %d completion tokens%n", cases.size(), promptTokens,
                completionTokens);
        assertThat(failures).isEmpty();
    }

    /** Reserva confirmada num voo do seeder, daqui a 20 dias. */
    private String[] confirmedBooking(UUID owner) throws Exception {
        String date = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(20).toString();
        String flights = mvc.perform(get("/flights").with(user(owner)).param("origin", "GRU").param("destination", "GIG")
                .param("date", date)).andReturn().getResponse().getContentAsString();
        String flightId = JsonPath.read(flights, "$[0].id");
        String fare = JsonPath.read(flights, "$[0].fares[0].fareClass");
        String key = UUID.randomUUID().toString();
        String held = mvc.perform(post("/bookings").with(user(owner)).header("Idempotency-Key", key)
                        .contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("flightId", flightId, "fareClass", fare, "passengers",
                                List.of(Map.of("fullName", "Eval Teste", "documentNumber", "EV1", "birthDate", "1990-01-01"))))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(held, "$.id");
        mvc.perform(post("/bookings/{id}/confirm", id).with(user(owner)).header("Idempotency-Key", key + "-c")
                .contentType(APPLICATION_JSON).content("{\"cardToken\":\"tok_visa\"}")).andExpect(status().isOk());
        return new String[] {id, JsonPath.read(held, "$.code")};
    }

    private static RequestPostProcessor user(UUID id) {
        return jwt().jwt(token -> token.subject(id.toString()));
    }
}
