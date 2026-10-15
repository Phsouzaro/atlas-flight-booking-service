package com.atlas.booking.internal;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.atlas.booking.BookingCancelled;
import com.atlas.booking.BookingRebooked;
import com.atlas.booking.ReleasedSeats;
import com.atlas.catalog.AvailabilityApi;

/**
 * Devolve ao catálogo os assentos de uma reserva cancelada ou remarcada.
 * <p>
 * Fica no booking e não no catalog porque o booking já depende do catalog (AvailabilityApi); um
 * listener no catalog ouvindo evento do booking fecharia um ciclo, que o ModularityTests não aceita.
 * É síncrono: roda na transação de quem publicou, então assento e status mudam juntos ou nada muda.
 */
@Component
class SeatReturnListener {

    private final AvailabilityApi availability;

    SeatReturnListener(AvailabilityApi availability) {
        this.availability = availability;
    }

    @EventListener
    void on(BookingCancelled event) {
        event.releasedSeats().forEach(this::release);
    }

    @EventListener
    void on(BookingRebooked event) {
        event.releasedSeats().forEach(this::release);
    }

    private void release(ReleasedSeats seats) {
        availability.releaseSeats(seats.flightId(), seats.fareClass(), seats.seats());
    }
}
