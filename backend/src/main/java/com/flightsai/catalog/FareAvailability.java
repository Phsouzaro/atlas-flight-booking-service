package com.flightsai.catalog;

import java.time.Instant;
import java.util.UUID;

import com.flightsai.shared.Money;

/** Situação atual de uma tarifa, para o módulo de reservas. */
public record FareAvailability(
        UUID flightId,
        String flightNumber,
        String fareClass,
        int availableSeats,
        Money price,
        Instant departureAt) {
}
