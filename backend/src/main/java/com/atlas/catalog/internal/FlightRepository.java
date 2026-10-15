package com.atlas.catalog.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface FlightRepository extends JpaRepository<Flight, UUID>, JpaSpecificationExecutor<Flight> {

    /** Aeroportos, assentos e classes vêm na mesma consulta: sem N+1 ao montar a resposta. */
    @Override
    @EntityGraph(attributePaths = {"origin", "destination", "seats", "seats.fareClass"})
    List<Flight> findAll(Specification<Flight> spec, Sort sort);

    @EntityGraph(attributePaths = {"origin", "destination", "seats", "seats.fareClass"})
    Optional<Flight> findWithSeatsById(UUID id);
}
