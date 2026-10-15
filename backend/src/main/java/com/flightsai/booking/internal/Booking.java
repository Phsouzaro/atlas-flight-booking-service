package com.flightsai.booking.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Persistable;

import com.flightsai.booking.BookingStatus;
import com.flightsai.booking.PassengerInfo;
import com.flightsai.catalog.FareAvailability;
import com.flightsai.shared.Money;

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

    List<BookingItem> getItems() {
        return items;
    }

    List<Passenger> getPassengers() {
        return passengers;
    }
}
