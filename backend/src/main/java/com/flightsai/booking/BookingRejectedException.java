package com.flightsai.booking;

/**
 * A reserva não pôde seguir por uma regra de negócio. {@link #reason()} diz qual, para quem chama
 * (REST ou tool da IA) decidir o que fazer. A mensagem é segura para mostrar ao cliente.
 */
public class BookingRejectedException extends RuntimeException {

    public enum Reason {
        FARE_NOT_FOUND,
        FLIGHT_DEPARTED,
        SEATS_UNAVAILABLE,
        INVALID_STATE,
        HOLD_EXPIRED,
        PAYMENT_DECLINED
    }

    private final Reason reason;

    public BookingRejectedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
