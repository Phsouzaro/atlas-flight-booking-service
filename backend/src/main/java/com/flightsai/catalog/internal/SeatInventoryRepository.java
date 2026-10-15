package com.flightsai.catalog.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface SeatInventoryRepository extends JpaRepository<SeatInventory, UUID> {

    @EntityGraph(attributePaths = {"flight", "fareClass"})
    Optional<SeatInventory> findByFlightIdAndFareClassCode(UUID flightId, String fareClassCode);
}
