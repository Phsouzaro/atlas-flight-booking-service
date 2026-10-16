package com.atlas.identity.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Apaga famílias de refresh token mortas (todos os tokens revogados ou vencidos) há mais que a retenção (7 dias
 * por padrão). A família sai inteira, num comando só, por causa da referência {@code replaced_by} entre tokens.
 * <p>
 * Os dias de retenção mantêm os tokens revogados por perto para o alerta de reuso; depois disso um token
 * velho é só desconhecido, e a resposta (401) é a mesma.
 */
@Component
class RefreshTokenCleanup {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanup.class);

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final Duration retention;

    RefreshTokenCleanup(JdbcTemplate jdbc, Clock clock,
                        @Value("${app.security.refresh.retention:7d}") Duration retention) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.retention = retention;
    }

    /** @return quantos tokens foram apagados */
    @Scheduled(fixedDelayString = "${app.cleanup.interval:PT1H}", initialDelayString = "${app.cleanup.initial-delay:PT5M}")
    int purge() {
        Instant now = clock.instant();
        int deleted = jdbc.update("""
                DELETE FROM refresh_tokens WHERE family_id IN (
                    SELECT family_id FROM refresh_tokens
                     GROUP BY family_id
                    HAVING bool_and(revoked_at IS NOT NULL OR expires_at <= ?)
                       AND max(coalesce(revoked_at, expires_at)) < ?)""",
                now.atOffset(ZoneOffset.UTC), now.minus(retention).atOffset(ZoneOffset.UTC));
        if (deleted > 0) {
            log.info("Purged {} dead refresh tokens", deleted);
        }
        return deleted;
    }
}
