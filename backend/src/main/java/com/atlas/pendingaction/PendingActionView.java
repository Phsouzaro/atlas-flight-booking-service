package com.atlas.pendingaction;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

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
        @Schema(nullable = true) Map<String, Object> result,
        Instant createdAt,
        Instant expiresAt,
        @Schema(nullable = true) Instant decidedAt) {
}
