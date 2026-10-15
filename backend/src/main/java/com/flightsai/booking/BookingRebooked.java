package com.flightsai.booking;

import java.util.List;
import java.util.UUID;

/**
 * Publicado na transação da remarcação. {@code releasedSeats} são os assentos da reserva antiga;
 * os da nova já foram reservados antes do evento.
 */
public record BookingRebooked(UUID fromBookingId, UUID toBookingId, UUID userId, List<ReleasedSeats> releasedSeats) {
}
