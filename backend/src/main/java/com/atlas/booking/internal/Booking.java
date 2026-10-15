package com.atlas.booking.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Persistable;

import com.atlas.booking.BookingStatus;
import com.atlas.booking.PassengerInfo;
import com.atlas.catalog.FareAvailability;
import com.atlas.shared.Money;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "bookings")
class Booking implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "hold_expires_at", nullable = false)
    private Instant holdExpiresAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "expired_at")
    private Instant expiredAt;

    @Column(name = "payment_reference")
    private String paymentReference;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "refund_amount")
    private BigDecimal refundAmount;

    @Column(name = "refund_reference")
    private String refundReference;

    @Column(name = "rebooked_at")
    private Instant rebookedAt;

    @Column(name = "rebooked_to_id")
    private UUID rebookedToId;

    @Column(name = "rebooked_from_id", updatable = false)
    private UUID rebookedFromId;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookingItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<Passenger> passengers = new ArrayList<>();

    /** Id é gerado na aplicação; este flag evita o SELECT extra que o merge faria no save(). */
    @Transient
    private boolean isNew = true;

    protected Booking() {
    }

    /** Novo hold. Os assentos já devem ter sido reservados no catálogo. */
    static Booking hold(UUID userId, String code, Instant now, Instant holdExpiresAt,
                        FareAvailability fare, List<PassengerInfo> passengers) {
        Booking booking = new Booking();
        booking.id = UUID.randomUUID();
        booking.userId = userId;
        booking.code = code;
        booking.status = BookingStatus.HELD;
        booking.createdAt = now;
        booking.holdExpiresAt = holdExpiresAt;
        booking.items.add(new BookingItem(booking, fare, passengers.size()));
        for (int i = 0; i < passengers.size(); i++) {
            booking.passengers.add(new Passenger(booking, i, passengers.get(i)));
        }
        booking.totalAmount = fare.price().amount().multiply(BigDecimal.valueOf(passengers.size()));
        booking.currency = fare.price().currency();
        return booking;
    }

    /**
     * Reserva nova, já confirmada, com os mesmos passageiros de {@code original} no voo {@code target}.
     * Os assentos do voo novo já devem ter sido reservados no catálogo.
     */
    static Booking rebookedFrom(Booking original, String code, Instant now, FareAvailability target,
                                String paymentReference) {
        List<PassengerInfo> passengers = original.passengers.stream().map(Passenger::toView).toList();
        Booking booking = hold(original.userId, code, now, now, target, passengers);
        booking.rebookedFromId = original.id;
        booking.confirm(now, paymentReference);
        return booking;
    }

    void confirm(Instant now, String paymentReference) {
        requireStatus(BookingStatus.HELD);
        this.status = BookingStatus.CONFIRMED;
        this.confirmedAt = now;
        this.paymentReference = paymentReference;
    }

    void expire(Instant now) {
        requireStatus(BookingStatus.HELD);
        this.status = BookingStatus.EXPIRED;
        this.expiredAt = now;
    }

    void cancel(Instant now, Money refund, String refundReference) {
        requireStatus(BookingStatus.CONFIRMED);
        this.status = BookingStatus.CANCELLED;
        this.cancelledAt = now;
        this.refundAmount = refund.amount();
        this.refundReference = refundReference;
    }

    void markRebooked(Instant now, UUID newBookingId) {
        requireStatus(BookingStatus.CONFIRMED);
        this.status = BookingStatus.REBOOKED;
        this.rebookedAt = now;
        this.rebookedToId = newBookingId;
    }

    /** A única reserva desta: hoje toda reserva tem um item só. */
    BookingItem item() {
        return items.get(0);
    }

    private void requireStatus(BookingStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Booking %s is %s, expected %s".formatted(id, status, expected));
        }
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    UUID getUserId() {
        return userId;
    }

    String getCode() {
        return code;
    }

    BookingStatus getStatus() {
        return status;
    }

    Money total() {
        return new Money(totalAmount, currency);
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getHoldExpiresAt() {
        return holdExpiresAt;
    }

    Instant getConfirmedAt() {
        return confirmedAt;
    }

    Instant getExpiredAt() {
        return expiredAt;
    }

    String getPaymentReference() {
        return paymentReference;
    }

    Instant getCancelledAt() {
        return cancelledAt;
    }

    Instant getRebookedAt() {
        return rebookedAt;
    }

    /** {@code null} enquanto não houver estorno registrado. */
    Money refund() {
        return refundAmount == null ? null : new Money(refundAmount, currency);
    }

    UUID getRebookedToId() {
        return rebookedToId;
    }

    UUID getRebookedFromId() {
        return rebookedFromId;
    }

    List<BookingItem> getItems() {
        return items;
    }

    List<Passenger> getPassengers() {
        return passengers;
    }
}
