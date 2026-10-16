package com.atlas.shared.ratelimit;

import java.time.Duration;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limites por rota e de tentativas de login erradas. Os valores ficam no {@code application.yml}.
 *
 * @param enabled       desliga tudo (os testes que não são de rate limit desligam)
 * @param rules         regras por nome; a mesma rota pode ter várias (ex.: por minuto e por dia)
 * @param loginFailures tentativas de senha errada por email, contadas só quando falham
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(boolean enabled, Map<String, Rule> rules, Limit loginFailures) {

    public RateLimitProperties {
        rules = rules == null ? Map.of() : Map.copyOf(rules);
        if (loginFailures == null) {
            loginFailures = new Limit(5, Duration.ofMinutes(15));
        }
    }

    /** Quem a regra conta: o IP de origem ou o usuário do token. */
    public enum Key {
        IP,
        USER
    }

    /**
     * @param method método HTTP
     * @param path   padrão de caminho do Spring, ex.: {@code /assistant/conversations/*}{@code /messages}
     */
    public record Rule(String method, String path, Key key, int capacity, Duration period) {

        public Limit limit() {
            return new Limit(capacity, period);
        }
    }

    /** {@code capacity} pedidos a cada {@code period}, repostos aos poucos ao longo do período. */
    public record Limit(int capacity, Duration period) {

        public Limit {
            if (capacity <= 0 || period == null || period.isZero() || period.isNegative()) {
                throw new IllegalArgumentException("Rate limit needs a positive capacity and period");
            }
        }
    }
}
