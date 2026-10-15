package com.flightsai.pendingaction.internal;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import com.flightsai.pendingaction.PendingActionStatus;
import com.flightsai.pendingaction.PendingActionView;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "pending_actions")
class PendingAction implements Persistable<UUID> {

    /** Só os status gravados; {@code EXPIRED} é derivado. */
    enum StoredStatus {
        PENDING,
        CONFIRMED,
        REJECTED
    }

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StoredStatus status;

    @Column(nullable = false, updatable = false)
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private Map<String, Object> payload;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> result;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Transient
    private boolean isNew = true;

    protected PendingAction() {
    }

    PendingAction(UUID userId, String type, String summary, Map<String, Object> payload, Instant now, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.type = type;
        this.status = StoredStatus.PENDING;
        this.summary = summary;
        this.payload = payload;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    boolean isPending() {
        return status == StoredStatus.PENDING;
    }

    boolean isExpired(Instant now) {
        return isPending() && !now.isBefore(expiresAt);
    }

    void confirm(Instant now, Map<String, Object> result) {
        this.status = StoredStatus.CONFIRMED;
        this.decidedAt = now;
        this.result = result;
    }

    void reject(Instant now) {
        this.status = StoredStatus.REJECTED;
        this.decidedAt = now;
    }

    PendingActionView toView(Instant now) {
        PendingActionStatus shown = isExpired(now) ? PendingActionStatus.EXPIRED : PendingActionStatus.valueOf(status.name());
        return new PendingActionView(id, type, shown, summary, payload, result, createdAt, expiresAt, decidedAt);
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    String getType() {
        return type;
    }

    Map<String, Object> getPayload() {
        return payload;
    }

    StoredStatus getStatus() {
        return status;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }
}
