package com.atlas.booking;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.atlas.shared.Money;

/**
 * Reserva como o dono vê.
 *
 * @param code           localizador de 6 caracteres
 * @param holdExpiresAt  até quando um {@code HELD} pode ser confirmado
 * @param refund         quanto foi devolvido, quando {@code CANCELLED}
 * @param rebookedFromId reserva original, quando esta nasceu de uma remarcação
 * @param rebookedToId   reserva nova, quando esta foi remarcada ({@code REBOOKED})
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
        Instant cancelledAt,
        Instant rebookedAt,
        String paymentReference,
        Money refund,
        UUID rebookedFromId,
        UUID rebookedToId,
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
