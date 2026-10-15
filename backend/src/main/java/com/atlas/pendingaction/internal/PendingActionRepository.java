package com.atlas.pendingaction.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

interface PendingActionRepository extends JpaRepository<PendingAction, UUID> {

    List<PendingAction> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<PendingAction> findByIdAndUserId(UUID id, UUID userId);

    /** Trava a ação: dois cliques em Confirmar ao mesmo tempo executam uma vez só. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PendingAction a where a.id = :id and a.userId = :userId")
    Optional<PendingAction> lockByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);
}
