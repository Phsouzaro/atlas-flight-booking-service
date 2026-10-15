package com.atlas.catalog;

import java.time.Instant;
import java.util.UUID;

import com.atlas.shared.Money;

/** Situação atual de uma tarifa, para o módulo de reservas. */
public record FareAvailability(
        UUID flightId,
        String flightNumber,
        String origin,
        String destination,
        Instant departureAt,
        String fareClass,
        int availableSeats,
        Money price) {
}
