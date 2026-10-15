package com.atlas.booking.internal;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.atlas.booking.internal.BookingActionPayloads.CancelPayload;
import com.atlas.booking.internal.BookingActionPayloads.RebookPayload;
import com.atlas.pendingaction.PendingActionExecution;
import com.atlas.pendingaction.PendingActionHandler;

/** Executa o cancelamento quando o usuário confirma a ação pendente. */
@Component
class CancelBookingHandler implements PendingActionHandler {

    private final BookingService bookings;
    private final ObjectMapper json;

    CancelBookingHandler(BookingService bookings, ObjectMapper json) {
        this.bookings = bookings;
        this.json = json;
    }

    @Override
    public String type() {
        return BookingActionPayloads.CANCEL;
    }

    @Override
    public Object execute(PendingActionExecution execution) {
        return bookings.executeCancellation(execution.userId(), json.convertValue(execution.payload(), CancelPayload.class));
    }
}

/** Executa a remarcação quando o usuário confirma a ação pendente. */
@Component
class RebookBookingHandler implements PendingActionHandler {

    private final BookingService bookings;
    private final ObjectMapper json;

    RebookBookingHandler(BookingService bookings, ObjectMapper json) {
        this.bookings = bookings;
        this.json = json;
    }

    @Override
    public String type() {
        return BookingActionPayloads.REBOOK;
    }

    @Override
    public Object execute(PendingActionExecution execution) {
        return bookings.executeRebooking(execution.userId(), json.convertValue(execution.payload(), RebookPayload.class),
                execution.cardToken());
    }
}
