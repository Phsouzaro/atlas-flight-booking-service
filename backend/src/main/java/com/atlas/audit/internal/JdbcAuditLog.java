package com.atlas.audit.internal;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.micrometer.core.instrument.MeterRegistry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.atlas.audit.AuditEntry;
import com.atlas.audit.AuditLog;

@Component
class JdbcAuditLog implements AuditLog {

    /** Contador por ação auditada (BOOKING_HELD, BOOKING_CONFIRMED...): toda escrita de negócio passa por aqui. */
    static final String EVENTS_METRIC = "atlas.business.events";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;
    private final MeterRegistry meters;

    JdbcAuditLog(JdbcTemplate jdbc, ObjectMapper json, Clock clock, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
        this.meters = meters;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditEntry entry) {
        jdbc.update("INSERT INTO audit_log (id, occurred_at, actor_user_id, action, entity_type, entity_id, details) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)",
                UUID.randomUUID(),
                clock.instant().atOffset(ZoneOffset.UTC),
                entry.actorUserId(),
                entry.action(),
                entry.entityType(),
                entry.entityId(),
                toJson(entry));
        // métrica só depois do commit: escrita desfeita não conta
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                meters.counter(EVENTS_METRIC, "action", entry.action()).increment();
            }
        });
    }

    private String toJson(AuditEntry entry) {
        try {
            return json.writeValueAsString(entry.details());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Audit details are not serializable: " + entry.action(), e);
        }
    }
}
