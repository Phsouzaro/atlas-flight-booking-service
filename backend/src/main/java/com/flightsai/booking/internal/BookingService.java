package com.flightsai.booking.internal;

import static com.flightsai.booking.BookingRejectedException.Reason.FARE_NOT_FOUND;
import static com.flightsai.booking.BookingRejectedException.Reason.FLIGHT_DEPARTED;
import static com.flightsai.booking.BookingRejectedException.Reason.HOLD_EXPIRED;
import static com.flightsai.booking.BookingRejectedException.Reason.INVALID_STATE;
import static com.flightsai.booking.BookingRejectedException.Reason.PAYMENT_DECLINED;
import static com.flightsai.booking.BookingRejectedException.Reason.SEATS_UNAVAILABLE;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flightsai.audit.AuditEntry;
import com.flightsai.audit.AuditLog;
import com.flightsai.booking.BookingApi;
import com.flightsai.booking.BookingRejectedException;
import com.flightsai.booking.BookingStatus;
import com.flightsai.booking.BookingView;
import com.flightsai.booking.HoldBookingCommand;
import com.flightsai.catalog.AvailabilityApi;
import com.flightsai.catalog.FareAvailability;
import com.flightsai.identity.CurrentUser;
import com.flightsai.payment.PaymentGateway;
import com.flightsai.payment.PaymentRequest;
import com.flightsai.payment.PaymentResult;

@Service
class BookingService implements BookingApi {

    static final String ENTITY = "booking";

    private final BookingRepository bookings;
    private final BookingCodeGenerator codes;
    private final AvailabilityApi availability;
    private final PaymentGateway payments;
    private final AuditLog audit;
    private final CurrentUser currentUser;
    private final BookingProperties props;
    private final Clock clock;

    BookingService(BookingRepository bookings, BookingCodeGenerator codes, AvailabilityApi availability,
                   PaymentGateway payments, AuditLog audit, CurrentUser currentUser, BookingProperties props,
                   Clock clock) {
        this.bookings = bookings;
        this.codes = codes;
        this.availability = availability;
        this.payments = payments;
        this.audit = audit;
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
                booking.getPaymentReference(),
                booking.getItems().stream().map(BookingItem::toView).toList(),
                booking.getPassengers().stream().map(Passenger::toView).toList());
    }
}
