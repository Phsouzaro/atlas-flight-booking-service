package com.flightsai.booking.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flightsai.booking.BookingView;
import com.flightsai.booking.HoldBookingCommand;
import com.flightsai.identity.CurrentUser;
import com.flightsai.shared.idempotency.IdempotentExecutor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/bookings")
class BookingController {

    private final BookingService bookings;
    private final IdempotentExecutor idempotency;
    private final CurrentUser currentUser;

    BookingController(BookingService bookings, IdempotentExecutor idempotency, CurrentUser currentUser) {
        this.bookings = bookings;
        this.idempotency = idempotency;
        this.currentUser = currentUser;
    }

    /** Cria um hold. Repetir com a mesma {@code Idempotency-Key} devolve a mesma reserva. */
    @PostMapping
    ResponseEntity<?> hold(@RequestHeader(IdempotentExecutor.HEADER) String idempotencyKey,
                           @Valid @RequestBody HoldBookingCommand command) {
        return idempotency.execute(currentUser.id(), idempotencyKey, "booking.hold", command,
                () -> ResponseEntity.status(HttpStatus.CREATED).body(bookings.hold(command)));
    }

    /** Paga e confirma um hold. Repetir com a mesma {@code Idempotency-Key} não cobra de novo. */
    @PostMapping("/{id}/confirm")
    ResponseEntity<?> confirm(@RequestHeader(IdempotentExecutor.HEADER) String idempotencyKey,
                              @PathVariable UUID id,
                              @Valid @RequestBody ConfirmBookingRequest request) {
        return idempotency.execute(currentUser.id(), idempotencyKey, "booking.confirm",
                new ConfirmKey(id, request.cardToken()),
                () -> ResponseEntity.ok(bookings.confirm(id, request.cardToken())));
    }

    @GetMapping
    List<BookingView> myBookings() {
        return bookings.myBookings();
    }

    @GetMapping("/{id}")
    BookingView booking(@PathVariable UUID id) {
        return bookings.findMyBooking(id).orElseThrow(() -> new BookingNotFoundException(id));
    }

    /** @param cardToken token do cartão gerado pelo front; no pagamento simulado, tok_decline recusa */
    record ConfirmBookingRequest(@NotBlank @Size(max = 100) String cardToken) {
    }

    /** O que identifica uma confirmação para a Idempotency-Key: reserva e token. */
    record ConfirmKey(UUID bookingId, String cardToken) {
    }
}
