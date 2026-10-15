package com.atlas.audit.internal;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.atlas.audit.AuditEntry;
import com.atlas.audit.AuditLog;

@Component
class JdbcAuditLog implements AuditLog {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    JdbcAuditLog(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
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
    }

    private String toJson(AuditEntry entry) {
        try {
            return json.writeValueAsString(entry.details());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Audit details are not serializable: " + entry.action(), e);
        }
    }
}
