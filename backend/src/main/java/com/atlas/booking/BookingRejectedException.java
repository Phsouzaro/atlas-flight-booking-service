package com.atlas.booking;

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
        PAYMENT_DECLINED,
        /** a ação cobra algo e não veio cardToken na confirmação */
        PAYMENT_REQUIRED,
        /** os valores mudaram entre a cotação e a confirmação; é preciso cotar de novo */
        QUOTE_CHANGED,
        /** remarcação para outra rota, para o mesmo voo e tarifa, ou de reserva já remarcada */
        REBOOKING_NOT_ALLOWED
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
