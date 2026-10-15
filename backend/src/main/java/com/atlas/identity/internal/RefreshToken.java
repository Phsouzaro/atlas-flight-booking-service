package com.atlas.identity.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/** Um refresh token. O valor em si nunca é guardado, só o hash. */
@Entity
@Table(name = "refresh_tokens")
class RefreshToken implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "family_expires_at", nullable = false, updatable = false)
    private Instant familyExpiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by")
    private UUID replacedBy;

    @Transient
    private boolean isNew = true;

    protected RefreshToken() {
    }

    private RefreshToken(UUID userId, UUID familyId, String tokenHash, Instant now, Instant expiresAt,
                         Instant familyExpiresAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.createdAt = now;
        this.familyExpiresAt = familyExpiresAt;
        this.expiresAt = expiresAt.isAfter(familyExpiresAt) ? familyExpiresAt : expiresAt;
    }

    /** Primeiro token de um login: começa uma família nova. */
    static RefreshToken start(UUID userId, String tokenHash, Instant now, RefreshTokenProperties props) {
        return new RefreshToken(userId, UUID.randomUUID(), tokenHash, now, now.plus(props.ttl()),
                now.plus(props.maxLifetime()));
    }

    /** Sucessor na rotação: mesma família e mesmo teto, inatividade recomeçando agora. */
    RefreshToken next(String tokenHash, Instant now, RefreshTokenProperties props) {
        return new RefreshToken(userId, familyId, tokenHash, now, now.plus(props.ttl()), familyExpiresAt);
    }

    void revoke(Instant now, UUID replacedBy) {
        this.revokedAt = now;
        this.replacedBy = replacedBy;
    }

    boolean isRevoked() {
        return revokedAt != null;
    }

    boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /** Revogado por rotação (tem sucessor), não por logout. */
    boolean wasRotated() {
        return replacedBy != null;
    }

    /** Foi trocado há pouco: reapresentá-lo agora é provavelmente outra aba renovando ao mesmo tempo. */
    boolean wasJustRotated(Instant now, Duration grace) {
        return replacedBy != null && revokedAt != null && now.isBefore(revokedAt.plus(grace));
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

    UUID getUserId() {
        return userId;
    }

    UUID getFamilyId() {
        return familyId;
    }
}
