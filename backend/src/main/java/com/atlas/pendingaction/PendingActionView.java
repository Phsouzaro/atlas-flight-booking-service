package com.atlas.pendingaction;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Ação pendente como o dono vê.
 *
 * @param result resposta da execução, só quando {@code CONFIRMED}
 */
public record PendingActionView(
        UUID id,
        String type,
        PendingActionStatus status,
        String summary,
        Map<String, Object> payload,
        Map<String, Object> result,
        Instant createdAt,
        Instant expiresAt,
        Instant decidedAt) {
}
