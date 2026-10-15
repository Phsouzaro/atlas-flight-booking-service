package com.atlas.shared.idempotency;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Apaga respostas guardadas por Idempotency-Key depois da retenção (24h por padrão). Um cliente repete um
 * POST em segundos ou minutos; guardar para sempre só faz a tabela crescer.
 */
@Component
public class IdempotencyKeyCleanup {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyCleanup.class);

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final Duration retention;

    IdempotencyKeyCleanup(JdbcTemplate jdbc, Clock clock, @Value("${app.idempotency.retention:24h}") Duration retention) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.retention = retention;
    }

    /** @return quantas chaves foram apagadas */
    @Scheduled(fixedDelayString = "${app.cleanup.interval:PT1H}", initialDelayString = "${app.cleanup.initial-delay:PT5M}")
    public int purge() {
        int deleted = jdbc.update("DELETE FROM idempotency_keys WHERE created_at < ?",
                clock.instant().minus(retention).atOffset(ZoneOffset.UTC));
        if (deleted > 0) {
            log.info("Purged {} idempotency keys older than {}", deleted, retention);
        }
        return deleted;
    }
}
