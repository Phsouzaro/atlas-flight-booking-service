package com.atlas.audit;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Uma escrita auditada.
 *
 * @param actorUserId quem fez; {@code null} quando é o sistema (ex.: job de expiração)
 * @param action      o que aconteceu, ex.: {@code BOOKING_HELD}
 * @param entityType  tipo da entidade afetada, ex.: {@code booking}
 * @param entityId    id da entidade afetada
 * @param details     dados extras serializados em JSON. Nunca colocar PII aqui.
 */
public record AuditEntry(UUID actorUserId, String action, String entityType, String entityId,
                         Map<String, Object> details) {

    public AuditEntry {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(entityId, "entityId");
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
