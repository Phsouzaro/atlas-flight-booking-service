package com.atlas.catalog.internal;

import java.time.Instant;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/** Peças da busca de voos, combinadas pelo {@link CatalogService}. */
final class FlightSpecifications {

    private FlightSpecifications() {
    }

    static Specification<Flight> route(String originCode, String destinationCode) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("origin").get("code"), originCode),
                cb.equal(root.get("destination").get("code"), destinationCode));
    }

    // Partida a partir de from (inclusive) e antes de to (exclusive). Sem colchetes no Javadoc: o
    // parser JSON simples que o Spring Modulith usa para ler os comentários se perde com eles.
    static Specification<Flight> departsBetween(Instant from, Instant to) {
        return (root, query, cb) -> cb.and(
                cb.greaterThanOrEqualTo(root.get("departureAt"), from),
                cb.lessThan(root.get("departureAt"), to));
    }

    /** Pelo menos uma classe tarifária com assento para todos os passageiros. */
    static Specification<Flight> hasSeatsFor(int passengers) {
        return (root, query, cb) -> {
            Subquery<Integer> seats = query.subquery(Integer.class);
            Root<SeatInventory> inventory = seats.from(SeatInventory.class);
            seats.select(cb.literal(1)).where(
                    cb.equal(inventory.get("flight"), root),
                    cb.greaterThanOrEqualTo(inventory.get("availableSeats"), passengers));
            return cb.exists(seats);
        };
    }
}
