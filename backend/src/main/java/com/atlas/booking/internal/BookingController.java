package com.atlas.booking.internal;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.atlas.booking.BookingView;
import com.atlas.booking.CancellationQuote;
import com.atlas.booking.HoldBookingCommand;
import com.atlas.booking.RebookingProposal;
import com.atlas.identity.CurrentUser;
import com.atlas.shared.idempotency.IdempotentExecutor;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    @ApiResponse(responseCode = "201", description = "Hold criado (ou o mesmo, se a chave se repetir)",
            content = @Content(schema = @Schema(implementation = BookingView.class)))
    ResponseEntity<?> hold(@RequestHeader(IdempotentExecutor.HEADER) String idempotencyKey,
                           @Valid @RequestBody HoldBookingCommand command) {
        return idempotency.execute(currentUser.id(), idempotencyKey, "booking.hold", command,
                () -> ResponseEntity.status(HttpStatus.CREATED).body(bookings.hold(command)));
    }

    /** Paga e confirma um hold. Repetir com a mesma {@code Idempotency-Key} não cobra de novo. */
    @PostMapping("/{id}/confirm")
    @ApiResponse(responseCode = "200", description = "Reserva confirmada",
            content = @Content(schema = @Schema(implementation = BookingView.class)))
    ResponseEntity<?> confirm(@RequestHeader(IdempotentExecutor.HEADER) String idempotencyKey,
                              @PathVariable UUID id,
                              @Valid @RequestBody ConfirmBookingRequest request) {
        return idempotency.execute(currentUser.id(), idempotencyKey, "booking.confirm",
                new ConfirmKey(id, request.cardToken()),
                () -> ResponseEntity.ok(bookings.confirm(id, request.cardToken())));
    }

    /**
     * Cota o cancelamento e cria uma ação pendente. Não cancela nada: quem executa é
     * {@code POST /pending-actions/{id}/confirm}. Sem Idempotency-Key: repetir só cria outra proposta.
     */
    @PostMapping("/{id}/cancellation-quote")
    @ResponseStatus(HttpStatus.CREATED)
    CancellationQuote quoteCancellation(@PathVariable UUID id) {
        return bookings.quoteCancellation(id);
    }

    /** Propõe a remarcação e cria uma ação pendente. Mesma regra da cotação de cancelamento. */
    @PostMapping("/{id}/rebooking-proposal")
    @ResponseStatus(HttpStatus.CREATED)
    RebookingProposal proposeRebooking(@PathVariable UUID id, @Valid @RequestBody RebookingRequest request) {
        return bookings.proposeRebooking(id, request.flightId(), request.fareClass());
    }

    /** Desiste de um hold não pago e devolve os assentos na hora. */
    @PostMapping("/{id}/release")
    @ApiResponse(responseCode = "200", description = "Hold liberado (reserva CANCELLED, reembolso zero)",
            content = @Content(schema = @Schema(implementation = BookingView.class)))
    ResponseEntity<?> release(@RequestHeader(IdempotentExecutor.HEADER) String idempotencyKey, @PathVariable UUID id) {
        return idempotency.execute(currentUser.id(), idempotencyKey, "booking.release", id,
                () -> ResponseEntity.ok(bookings.releaseHold(id)));
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

    record RebookingRequest(@NotNull UUID flightId, @NotBlank String fareClass) {
    }
}
