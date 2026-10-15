package com.flightsai.booking;

/**
 * Ciclo de vida: {@code HELD -> CONFIRMED -> CANCELLED | REBOOKED}, e {@code HELD -> EXPIRED}.
 * {@code CANCELLED} e {@code REBOOKED} chegam na fase 3.
 */
public enum BookingStatus {
    HELD,
    CONFIRMED,
    CANCELLED,
    REBOOKED,
    EXPIRED
}
