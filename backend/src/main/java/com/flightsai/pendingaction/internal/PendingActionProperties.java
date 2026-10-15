package com.flightsai.pendingaction.internal;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param ttl quanto tempo uma proposta pode ser confirmada (padrão 10 min) */
@ConfigurationProperties(prefix = "app.pending-actions")
public record PendingActionProperties(Duration ttl) {

    public PendingActionProperties {
        if (ttl == null) {
            ttl = Duration.ofMinutes(10);
        }
    }
}
