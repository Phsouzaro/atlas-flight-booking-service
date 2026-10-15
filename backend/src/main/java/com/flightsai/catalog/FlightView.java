package com.flightsai.catalog;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Voo como o cliente vê. Partida no fuso da origem e chegada no fuso do destino, sempre com o
 * offset, para não haver ambiguidade.
 */
public record FlightView(
        UUID id,
        String flightNumber,
        AirportView origin,
        AirportView destination,
        OffsetDateTime departureAt,
        OffsetDateTime arrivalAt,
        long durationMinutes,
        List<FareOption> fares) {
}
