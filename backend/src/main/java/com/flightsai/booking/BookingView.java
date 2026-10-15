package com.flightsai.booking;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.flightsai.shared.Money;

/**
 * Reserva como o dono vê.
 *
 * @param code          localizador de 6 caracteres
 * @param holdExpiresAt até quando um {@code HELD} pode ser confirmado
 */
public record BookingView(
        UUID id,
        String code,
        BookingStatus status,
        Money total,
        Instant createdAt,
        Instant holdExpiresAt,
        Instant confirmedAt,
        Instant expiredAt,
        String paymentReference,
        List<Item> items,
        List<PassengerInfo> passengers) {

    /** Um trecho da reserva, com os dados do voo copiados no momento do hold. */
    public record Item(
            UUID flightId,
            String flightNumber,
            String origin,
            String destination,
            Instant departureAt,
            String fareClass,
            int seats,
            Money unitPrice) {
    }
}
