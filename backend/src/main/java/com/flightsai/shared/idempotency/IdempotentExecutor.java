package com.flightsai.shared.idempotency;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Executa um POST que muda estado no máximo uma vez por {@code Idempotency-Key} e usuário.
 * <p>
 * Tudo roda numa transação só: grava a chave, executa a ação e guarda a resposta. Uma segunda
 * requisição com a mesma chave, chegando ao mesmo tempo, fica bloqueada no índice único até a
 * primeira terminar e então recebe a resposta guardada. Se a ação falhar, a transação inteira
 * (chave inclusa) é desfeita e o cliente pode tentar de novo com a mesma chave. Ou seja, só respostas
 * de sucesso são guardadas.
 */
@Component
public class IdempotentExecutor {

    public static final String HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";
    static final int MAX_KEY_LENGTH = 100;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private final Clock clock;

    IdempotentExecutor(JdbcTemplate jdbc, ObjectMapper json, PlatformTransactionManager txManager, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
    }

    /**
     * @param operation nome estável da operação (ex.: {@code booking.hold}); faz parte do hash, então
     *                  a mesma chave não serve para duas operações diferentes
     * @param request   o que identifica a requisição (corpo e, se for o caso, id do path)
     * @throws InvalidIdempotencyKeyException  chave vazia, longa demais ou com caractere inválido
     * @throws IdempotencyKeyReusedException   chave já usada com outra operação ou outro corpo
     */
    public ResponseEntity<?> execute(UUID userId, String key, String operation, Object request,
                                     Supplier<ResponseEntity<?>> action) {
        validate(key);
        String hash = hash(operation, request);
        return tx.execute(status -> {
            int inserted = jdbc.update("INSERT INTO idempotency_keys (user_id, idempotency_key, operation, "
                            + "request_hash, response_status, response_body, created_at) "
                            + "VALUES (?, ?, ?, ?, 0, 'null'::jsonb, ?) ON CONFLICT DO NOTHING",
                    userId, key, operation, hash, clock.instant().atOffset(ZoneOffset.UTC));
            if (inserted == 0) {
                return replay(userId, key, hash);
            }
            ResponseEntity<?> response = action.get();
            jdbc.update("UPDATE idempotency_keys SET response_status = ?, response_body = ?::jsonb "
                            + "WHERE user_id = ? AND idempotency_key = ?",
                    response.getStatusCode().value(), toJson(response.getBody()), userId, key);
            return response;
        });
    }

    private ResponseEntity<?> replay(UUID userId, String key, String hash) {
        Map<String, Object> stored = jdbc.queryForMap("SELECT request_hash, response_status, response_body::text AS body "
                + "FROM idempotency_keys WHERE user_id = ? AND idempotency_key = ?", userId, key);
        if (!hash.equals(stored.get("request_hash"))) {
            throw new IdempotencyKeyReusedException();
        }
        return ResponseEntity.status((Integer) stored.get("response_status"))
                .contentType(MediaType.APPLICATION_JSON)
                .header(REPLAYED_HEADER, "true")
                .body(stored.get("body"));
    }

    private static void validate(String key) {
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH || !key.matches("[\\x21-\\x7E]+")) {
            throw new InvalidIdempotencyKeyException();
        }
    }

    private String hash(String operation, Object request) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(operation.getBytes(UTF_8));
            sha.update((byte) '\n');
            sha.update(json.writeValueAsBytes(request));
            return HexFormat.of().formatHex(sha.digest());
        } catch (NoSuchAlgorithmException | JsonProcessingException e) {
            throw new IllegalStateException("Cannot hash request for " + operation, e);
        }
    }

    private String toJson(Object body) {
        try {
            return json.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot store idempotent response", e);
        }
    }
}
