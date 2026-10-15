package com.flightsai.booking;

import java.util.List;
import java.util.UUID;

import com.flightsai.shared.Money;

/**
 * Publicado na transação do cancelamento. Quem ouvir com {@code @EventListener} participa da mesma
 * transação: se falhar, o cancelamento é desfeito.
 */
public record BookingCancelled(UUID bookingId, UUID userId, Money refund, List<ReleasedSeats> releasedSeats) {
}
