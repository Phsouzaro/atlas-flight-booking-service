package com.atlas.booking;

import java.util.List;
import java.util.UUID;

/** Publicado na transação que expira um hold vencido; quem ouve devolve os assentos. */
public record BookingExpired(UUID bookingId, List<ReleasedSeats> releasedSeats) {
}
