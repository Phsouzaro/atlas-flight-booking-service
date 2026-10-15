package com.flightsai.booking.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

interface BookingRepository extends JpaRepository<Booking, UUID> {

    /** Itens vêm junto; passageiros por batch fetch (duas listas no mesmo graph não dá). */
    @EntityGraph(attributePaths = "items")
    List<Booking> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @EntityGraph(attributePaths = "items")
    Optional<Booking> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Trava a linha da reserva para mudar o status. Sem entity graph: o Postgres não aceita
     * {@code FOR UPDATE} no lado opcional de um outer join.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id and b.userId = :userId")
    Optional<Booking> lockByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    /**
     * Holds vencidos, já travados. {@code SKIP LOCKED} pula o que outra transação estiver mexendo
     * (uma confirmação em andamento, outra instância do job).
     */
    @Query(value = """
            SELECT * FROM bookings
             WHERE status = 'HELD' AND hold_expires_at <= :now
             ORDER BY hold_expires_at
             LIMIT :limit
             FOR UPDATE SKIP LOCKED""", nativeQuery = true)
    List<Booking> lockExpiredHolds(@Param("now") Instant now, @Param("limit") int limit);

    boolean existsByCode(String code);
}
