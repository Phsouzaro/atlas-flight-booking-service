package com.atlas.catalog.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SeatInventoryRepository extends JpaRepository<SeatInventory, UUID> {

    @EntityGraph(attributePaths = {"flight", "fareClass"})
    Optional<SeatInventory> findByFlightIdAndFareClassCode(UUID flightId, String fareClassCode);

    /**
     * Update condicional (ADR-004): o banco checa e decrementa no mesmo comando, então duas
     * transações disputando o último assento nunca passam as duas.
     *
     * @return 1 se reservou, 0 se não havia assento suficiente
     */
    @Modifying
    @Query("""
            update SeatInventory s
               set s.availableSeats = s.availableSeats - :seats
             where s.flight.id = :flightId
               and s.fareClass.code = :fareClass
               and s.availableSeats >= :seats""")
    int decrementAvailable(@Param("flightId") UUID flightId, @Param("fareClass") String fareClass,
                           @Param("seats") int seats);

    /** @return 1 se devolveu, 0 se passaria do total */
    @Modifying
    @Query("""
            update SeatInventory s
               set s.availableSeats = s.availableSeats + :seats
             where s.flight.id = :flightId
               and s.fareClass.code = :fareClass
               and s.availableSeats + :seats <= s.totalSeats""")
    int incrementAvailable(@Param("flightId") UUID flightId, @Param("fareClass") String fareClass,
                           @Param("seats") int seats);
}
