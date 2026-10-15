package com.flightsai.booking.internal;

import static com.flightsai.booking.BookingRejectedException.Reason.FARE_NOT_FOUND;
import static com.flightsai.booking.BookingRejectedException.Reason.FLIGHT_DEPARTED;
import static com.flightsai.booking.BookingRejectedException.Reason.HOLD_EXPIRED;
import static com.flightsai.booking.BookingRejectedException.Reason.INVALID_STATE;
import static com.flightsai.booking.BookingRejectedException.Reason.PAYMENT_DECLINED;
import static com.flightsai.booking.BookingRejectedException.Reason.PAYMENT_REQUIRED;
import static com.flightsai.booking.BookingRejectedException.Reason.QUOTE_CHANGED;
import static com.flightsai.booking.BookingRejectedException.Reason.REBOOKING_NOT_ALLOWED;
import static com.flightsai.booking.BookingRejectedException.Reason.SEATS_UNAVAILABLE;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flightsai.audit.AuditEntry;
import com.flightsai.audit.AuditLog;
import com.flightsai.booking.BookingApi;
import com.flightsai.booking.BookingCancelled;
import com.flightsai.booking.BookingRebooked;
import com.flightsai.booking.BookingRejectedException;
import com.flightsai.booking.BookingStatus;
import com.flightsai.booking.BookingView;
import com.flightsai.booking.CancellationQuote;
import com.flightsai.booking.HoldBookingCommand;
import com.flightsai.booking.RebookingProposal;
import com.flightsai.booking.ReleasedSeats;
import com.flightsai.booking.internal.BookingActionPayloads.CancelPayload;
import com.flightsai.booking.internal.BookingActionPayloads.RebookPayload;
import com.flightsai.booking.internal.FarePolicy.CancellationTerms;
import com.flightsai.booking.internal.FarePolicy.RebookingTerms;
import com.flightsai.catalog.AvailabilityApi;
import com.flightsai.catalog.FareAvailability;
import com.flightsai.catalog.FareRules;
import com.flightsai.catalog.FareRulesApi;
import com.flightsai.identity.CurrentUser;
import com.flightsai.payment.PaymentGateway;
import com.flightsai.payment.PaymentRequest;
import com.flightsai.payment.PaymentResult;
import com.flightsai.payment.RefundRequest;
import com.flightsai.pendingaction.PendingActionView;
import com.flightsai.pendingaction.PendingActions;
import com.flightsai.pendingaction.ProposedAction;
import com.flightsai.shared.Money;

@Service
class BookingService implements BookingApi {

    static final String ENTITY = "booking";

    private final BookingRepository bookings;
    private final BookingCodeGenerator codes;
    private final AvailabilityApi availability;
    private final FareRulesApi fareRules;
    private final PaymentGateway payments;
    private final PendingActions pendingActions;
    private final AuditLog audit;
    private final ApplicationEventPublisher events;
    private final CurrentUser currentUser;
    private final BookingProperties props;
    private final Clock clock;

    BookingService(BookingRepository bookings, BookingCodeGenerator codes, AvailabilityApi availability,
                   FareRulesApi fareRules, PaymentGateway payments, PendingActions pendingActions, AuditLog audit,
                   ApplicationEventPublisher events, CurrentUser currentUser, BookingProperties props, Clock clock) {
        this.bookings = bookings;
        this.codes = codes;
        this.availability = availability;
        this.fareRules = fareRules;
        this.payments = payments;
        this.pendingActions = pendingActions;
        this.audit = audit;
        this.events = events;
        this.currentUser = currentUser;
        this.props = props;
        this.clock = clock;
    }

    @Override
    @Transactional
    public BookingView hold(HoldBookingCommand command) {
        UUID userId = currentUser.id();
        int seats = command.passengers().size();
        if (seats < 1 || seats > HoldBookingCommand.MAX_PASSENGERS) {
            throw new IllegalArgumentException("Passengers must be between 1 and " + HoldBookingCommand.MAX_PASSENGERS);
        }
        FareAvailability fare = availability.findFare(command.flightId(), command.fareClass())
                .orElseThrow(() -> new BookingRejectedException(FARE_NOT_FOUND, "Flight or fare class not found"));
        Instant now = clock.instant();
        if (!fare.departureAt().isAfter(now)) {
            throw new BookingRejectedException(FLIGHT_DEPARTED, "Flight has already departed");
        }
        // update condicional (ADR-004): só passa se ainda houver assento no momento do UPDATE
        if (!availability.reserveSeats(fare.flightId(), fare.fareClass(), seats)) {
            throw new BookingRejectedException(SEATS_UNAVAILABLE, "Not enough seats available");
        }

        Booking booking = Booking.hold(userId, codes.next(), now, now.plus(props.holdTtl()), fare, command.passengers());
        bookings.save(booking);
        audit.record(new AuditEntry(userId, "BOOKING_HELD", ENTITY, booking.getId().toString(), Map.of(
                "code", booking.getCode(),
                "flightId", fare.flightId(),
                "fareClass", fare.fareClass(),
                "seats", seats,
                "total", booking.total().amount(),
                "currency", booking.total().currency(),
                "holdExpiresAt", booking.getHoldExpiresAt())));
        return toView(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingView> myBookings() {
        return bookings.findByUserIdOrderByCreatedAtDesc(currentUser.id()).stream().map(BookingService::toView).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BookingView> findMyBooking(UUID bookingId) {
        return bookings.findByIdAndUserId(bookingId, currentUser.id()).map(BookingService::toView);
    }

    /**
     * Cobra e confirma um hold. Fica fora da {@link BookingApi}: só o usuário confirma, pela UI.
     * <p>
     * A linha da reserva fica travada do início ao fim, então o job de expiração não consegue
     * expirar um hold que está sendo pago (ele pula linhas travadas).
     */
    @Transactional
    BookingView confirm(UUID bookingId, String cardToken) {
        UUID userId = currentUser.id();
        Booking booking = bookings.lockByIdAndUserId(bookingId, userId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        if (booking.getStatus() != BookingStatus.HELD) {
            throw new BookingRejectedException(INVALID_STATE, "Booking is " + booking.getStatus());
        }
        Instant now = clock.instant();
        if (!now.isBefore(booking.getHoldExpiresAt())) {
            // o job devolve os assentos na próxima rodada
            throw new BookingRejectedException(HOLD_EXPIRED, "Hold expired at " + booking.getHoldExpiresAt());
        }

        PaymentResult payment = payments.charge(new PaymentRequest(booking.getId(), booking.total(), cardToken));
        if (!payment.approved()) {
            throw new BookingRejectedException(PAYMENT_DECLINED, payment.declineReason());
        }

        booking.confirm(now, payment.reference());
        audit.record(new AuditEntry(userId, "BOOKING_CONFIRMED", ENTITY, booking.getId().toString(), Map.of(
                "code", booking.getCode(),
                "paymentReference", payment.reference(),
                "total", booking.total().amount(),
                "currency", booking.total().currency())));
        return toView(booking);
    }

    /** Expira os holds vencidos e devolve os assentos. Chamado pelo {@link BookingExpirationJob}. */
    @Transactional
    int expireDueHolds() {
        Instant now = clock.instant();
        List<Booking> due = bookings.lockExpiredHolds(now, props.expirationBatchSize());
        for (Booking booking : due) {
            for (BookingItem item : booking.getItems()) {
                availability.releaseSeats(item.getFlightId(), item.getFareClassCode(), item.getSeats());
            }
            booking.expire(now);
            audit.record(new AuditEntry(null, "BOOKING_EXPIRED", ENTITY, booking.getId().toString(), Map.of(
                    "code", booking.getCode(),
                    "holdExpiresAt", booking.getHoldExpiresAt())));
        }
        return due.size();
    }

    // ---- cancelamento ---------------------------------------------------------------------------

    @Override
    @Transactional
    public CancellationQuote quoteCancellation(UUID bookingId) {
        Booking booking = bookings.findByIdAndUserId(bookingId, currentUser.id())
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        CancellationTerms terms = cancellationTerms(booking, clock.instant());

        PendingActionView action = pendingActions.propose(new ProposedAction(BookingActionPayloads.CANCEL,
                "Cancelar a reserva %s (voo %s). Reembolso: %s.".formatted(
                        booking.getCode(), booking.item().getFlightNumber(), format(terms.refund())),
                new CancelPayload(booking.getId(), terms.refund().amount(), terms.refund().currency())));
        return new CancellationQuote(booking.getId(), booking.getCode(), terms.paid(), terms.retained(), terms.refund(),
                terms.freeWindow(), terms.rule(), action.id(), action.expiresAt());
    }

    /**
     * Executa um cancelamento confirmado. Chamado pelo handler da ação pendente, dentro da transação da
     * confirmação. Recalcula o reembolso a partir do estado atual; se não bater com o cotado, recusa.
     */
    @Transactional
    BookingView executeCancellation(UUID userId, CancelPayload quoted) {
        Booking booking = bookings.lockByIdAndUserId(quoted.bookingId(), userId)
                .orElseThrow(() -> new BookingNotFoundException(quoted.bookingId()));
        Instant now = clock.instant();
        CancellationTerms terms = cancellationTerms(booking, now);
        if (!sameAmount(terms.refund(), quoted.refund(), quoted.currency())) {
            throw new BookingRejectedException(QUOTE_CHANGED,
                    "Refund changed from %s %s to %s; request a new quote".formatted(
                            quoted.refund(), quoted.currency(), format(terms.refund())));
        }

        String refundReference = terms.refund().amount().signum() > 0
                ? payments.refund(new RefundRequest(booking.getId(), booking.getPaymentReference(), terms.refund()))
                : null;
        booking.cancel(now, terms.refund(), refundReference);
        // o listener devolve os assentos na mesma transação
        events.publishEvent(new BookingCancelled(booking.getId(), userId, terms.refund(), releasedSeats(booking)));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("code", booking.getCode());
        details.put("refund", terms.refund().amount());
        details.put("currency", terms.refund().currency());
        details.put("freeWindow", terms.freeWindow());
        if (refundReference != null) {
            details.put("refundReference", refundReference);
        }
        audit.record(new AuditEntry(userId, "BOOKING_CANCELLED", ENTITY, booking.getId().toString(), details));
        return toView(booking);
    }

    private CancellationTerms cancellationTerms(Booking booking, Instant now) {
        requireConfirmed(booking);
        BookingItem item = booking.item();
        if (!item.getDepartureAt().isAfter(now)) {
            throw new BookingRejectedException(FLIGHT_DEPARTED, "Flight has already departed");
        }
        // a janela de 24h vale para a compra original, não para a reserva que nasceu de uma remarcação
        Instant purchasedAt = booking.getRebookedFromId() == null ? booking.getConfirmedAt() : null;
        return FarePolicy.cancellation(booking.total(), item.getSeats(), item.getDepartureAt(), purchasedAt,
                rulesFor(item.getFareClassCode()), now);
    }

    // ---- remarcação -----------------------------------------------------------------------------

    @Override
    @Transactional
    public RebookingProposal proposeRebooking(UUID bookingId, UUID newFlightId, String fareClass) {
        Booking booking = bookings.findByIdAndUserId(bookingId, currentUser.id())
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        FareAvailability target = findTargetFare(newFlightId, fareClass);
        RebookingTerms terms = rebookingTerms(booking, target, clock.instant());
        int seats = booking.item().getSeats();
        if (target.availableSeats() < seats) {
            // aviso cedo; quem garante o assento é o update condicional na confirmação
            throw new BookingRejectedException(SEATS_UNAVAILABLE, "Not enough seats available on the new flight");
        }

        PendingActionView action = pendingActions.propose(new ProposedAction(BookingActionPayloads.REBOOK,
                "Remarcar a reserva %s para o voo %s (%s). Valor a pagar: %s.".formatted(
                        booking.getCode(), target.flightNumber(), target.fareClass(), format(terms.amountDue())),
                new RebookPayload(booking.getId(), target.flightId(), target.fareClass(),
                        terms.amountDue().amount(), terms.amountDue().currency())));
        return new RebookingProposal(booking.getId(), booking.getCode(), target.flightId(), target.flightNumber(),
                target.departureAt(), target.fareClass(), seats, terms.changeFee(), terms.fareDifference(),
                terms.amountDue(), action.id(), action.expiresAt());
    }

    /**
     * Executa uma remarcação confirmada, tudo numa transação: reserva o voo novo, cobra a diferença,
     * cria a reserva nova, marca a antiga como REBOOKED e devolve os assentos dela. Qualquer falha
     * desfaz tudo.
     */
    @Transactional
    BookingView executeRebooking(UUID userId, RebookPayload quoted, String cardToken) {
        Booking original = bookings.lockByIdAndUserId(quoted.bookingId(), userId)
                .orElseThrow(() -> new BookingNotFoundException(quoted.bookingId()));
        Instant now = clock.instant();
        FareAvailability target = findTargetFare(quoted.newFlightId(), quoted.fareClass());
        RebookingTerms terms = rebookingTerms(original, target, now);
        if (!sameAmount(terms.amountDue(), quoted.amountDue(), quoted.currency())) {
            throw new BookingRejectedException(QUOTE_CHANGED,
                    "Amount due changed from %s %s to %s; request a new proposal".formatted(
                            quoted.amountDue(), quoted.currency(), format(terms.amountDue())));
        }
        int seats = original.item().getSeats();
        if (!availability.reserveSeats(target.flightId(), target.fareClass(), seats)) {
            throw new BookingRejectedException(SEATS_UNAVAILABLE, "Not enough seats available on the new flight");
        }

        String paymentReference = original.getPaymentReference();
        if (terms.amountDue().amount().signum() > 0) {
            if (cardToken == null || cardToken.isBlank()) {
                throw new BookingRejectedException(PAYMENT_REQUIRED,
                        "This rebooking costs " + format(terms.amountDue()) + "; cardToken is required");
            }
            PaymentResult payment = payments.charge(new PaymentRequest(original.getId(), terms.amountDue(), cardToken));
            if (!payment.approved()) {
                throw new BookingRejectedException(PAYMENT_DECLINED, payment.declineReason());
            }
            paymentReference = payment.reference();
        }

        Booking replacement = Booking.rebookedFrom(original, codes.next(), now, target, paymentReference);
        bookings.save(replacement);
        original.markRebooked(now, replacement.getId());
        events.publishEvent(new BookingRebooked(original.getId(), replacement.getId(), userId, releasedSeats(original)));

        audit.record(new AuditEntry(userId, "BOOKING_REBOOKED", ENTITY, original.getId().toString(), Map.of(
                "code", original.getCode(),
                "toBookingId", replacement.getId(),
                "amountDue", terms.amountDue().amount(),
                "currency", terms.amountDue().currency())));
        audit.record(new AuditEntry(userId, "BOOKING_CREATED_BY_REBOOKING", ENTITY, replacement.getId().toString(), Map.of(
                "code", replacement.getCode(),
                "fromBookingId", original.getId(),
                "flightId", target.flightId(),
                "fareClass", target.fareClass(),
                "seats", seats,
                "paymentReference", paymentReference)));
        return toView(replacement);
    }

    private RebookingTerms rebookingTerms(Booking booking, FareAvailability target, Instant now) {
        requireConfirmed(booking);
        BookingItem item = booking.item();
        if (!item.getDepartureAt().isAfter(now)) {
            throw new BookingRejectedException(FLIGHT_DEPARTED, "Current flight has already departed");
        }
        if (!target.departureAt().isAfter(now)) {
            throw new BookingRejectedException(FLIGHT_DEPARTED, "New flight has already departed");
        }
        if (!item.getOriginCode().equals(target.origin()) || !item.getDestinationCode().equals(target.destination())) {
            throw new BookingRejectedException(REBOOKING_NOT_ALLOWED, "Rebooking must keep the same route");
        }
        if (item.getFlightId().equals(target.flightId()) && item.getFareClassCode().equals(target.fareClass())) {
            throw new BookingRejectedException(REBOOKING_NOT_ALLOWED, "Booking is already on this flight and fare");
        }
        return FarePolicy.rebooking(item.unitPrice(), target.price(), item.getSeats(), rulesFor(item.getFareClassCode()));
    }

    private FareAvailability findTargetFare(UUID flightId, String fareClass) {
        return availability.findFare(flightId, fareClass)
                .orElseThrow(() -> new BookingRejectedException(FARE_NOT_FOUND, "Flight or fare class not found"));
    }

    // ---- apoio ----------------------------------------------------------------------------------

    private static void requireConfirmed(Booking booking) {
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BookingRejectedException(INVALID_STATE, "Booking is " + booking.getStatus());
        }
    }

    private FareRules rulesFor(String fareClass) {
        return fareRules.rulesFor(fareClass)
                .orElseThrow(() -> new IllegalStateException("No fare rules for " + fareClass));
    }

    private static List<ReleasedSeats> releasedSeats(Booking booking) {
        return booking.getItems().stream()
                .map(i -> new ReleasedSeats(i.getFlightId(), i.getFareClassCode(), i.getSeats()))
                .toList();
    }

    private static boolean sameAmount(Money current, BigDecimal quotedAmount, String quotedCurrency) {
        return current.currency().equals(quotedCurrency) && current.amount().compareTo(quotedAmount) == 0;
    }

    private static String format(Money money) {
        return money.currency() + " " + money.amount().toPlainString();
    }

    private static BookingView toView(Booking booking) {
        return new BookingView(
                booking.getId(),
                booking.getCode(),
                booking.getStatus(),
                booking.total(),
                booking.getCreatedAt(),
                booking.getHoldExpiresAt(),
                booking.getConfirmedAt(),
                booking.getExpiredAt(),
                booking.getCancelledAt(),
                booking.getRebookedAt(),
                booking.getPaymentReference(),
                booking.refund(),
                booking.getRebookedFromId(),
                booking.getRebookedToId(),
                booking.getItems().stream().map(BookingItem::toView).toList(),
                booking.getPassengers().stream().map(Passenger::toView).toList());
    }
}
