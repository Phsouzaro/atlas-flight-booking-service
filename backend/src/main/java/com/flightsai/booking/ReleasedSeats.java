package com.flightsai.booking;

import java.util.UUID;

/** Assentos que uma reserva deixou de ocupar. */
public record ReleasedSeats(UUID flightId, String fareClass, int seats) {
}
