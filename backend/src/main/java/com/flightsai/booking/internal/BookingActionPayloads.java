package com.flightsai.booking.internal;

import java.math.BigDecimal;
import java.util.UUID;

/** Tipos de ação pendente do módulo e o que cada uma guarda da cotação. */
final class BookingActionPayloads {

    static final String CANCEL = "BOOKING_CANCEL";
    static final String REBOOK = "BOOKING_REBOOK";

    private BookingActionPayloads() {
    }

    /** O reembolso cotado; na confirmação ele é recalculado e precisa bater. */
    record CancelPayload(UUID bookingId, BigDecimal refund, String currency) {
    }

    /** O voo e a tarifa escolhidos e o valor cotado; na confirmação tudo é recalculado e precisa bater. */
    record RebookPayload(UUID bookingId, UUID newFlightId, String fareClass, BigDecimal amountDue, String currency) {
    }
}
