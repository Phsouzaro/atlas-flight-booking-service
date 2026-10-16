package com.atlas.identity.internal;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

/** Fluxo completo contra um PostgreSQL real (a mesma imagem do docker-compose, com pgvector). */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "app.security.jwt.secret=test-secret-with-at-least-32-bytes-0123456789",
        "app.rate-limit.enabled=false"}) // os testes de limite ficam no RateLimitTest
class AuthFlowTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void registersLogsInAndReadsProfile() throws Exception {
        String email = uniqueEmail();

        register(email, "senha-forte-123")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.id").isNotEmpty());

        String body = login(email, "senha-forte-123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");

        mvc.perform(get("/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void normalizesEmailCase() throws Exception {
        String email = uniqueEmail();
        register(email, "senha-forte-123").andExpect(status().isCreated());

        login(email.toUpperCase(), "senha-forte-123").andExpect(status().isOk());
    }

    @Test
    void rejectsDuplicateEmailWithConflict() throws Exception {
        String email = uniqueEmail();
        register(email, "senha-forte-123").andExpect(status().isCreated());

        register(email, "outra-senha-456")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void rejectsWrongPassword() throws Exception {
        String email = uniqueEmail();
        register(email, "senha-forte-123").andExpect(status().isCreated());

        login(email, "senha-errada-999").andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsUnknownUserWithSameStatusAsWrongPassword() throws Exception {
        login(uniqueEmail(), "qualquer-senha-1").andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointRequiresToken() throws Exception {
        mvc.perform(get("/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/me").header("Authorization", "Bearer token-invalido"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidPayload() throws Exception {
        register("nao-e-um-email", "curta").andExpect(status().isBadRequest());
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    private ResultActions register(String email, String password) throws Exception {
        return postJson("/auth/register", Map.of("email", email, "password", password));
    }

    private ResultActions login(String email, String password) throws Exception {
        return postJson("/auth/login", Map.of("email", email, "password", password));
    }

    private ResultActions postJson(String path, Object payload) throws Exception {
        return mvc.perform(post(path)
                .contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(payload)));
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
