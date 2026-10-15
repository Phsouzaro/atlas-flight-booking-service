package com.atlas.identity.internal;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do JWT. O segredo é obrigatório e vem de variável de ambiente (JWT_SECRET):
 * sem ele a aplicação não sobe, o que é intencional (falhar cedo em vez de rodar inseguro).
 */
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(String secret, Duration ttl) {

    static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "app.security.jwt.secret precisa ter ao menos %d bytes (defina JWT_SECRET)"
                            .formatted(MIN_SECRET_BYTES));
        }
        if (ttl == null) {
            ttl = Duration.ofMinutes(15);
        }
    }
}
