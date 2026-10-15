package com.flightsai.booking.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.flightsai.booking.BookingView;
import com.flightsai.catalog.FareAvailability;
import com.flightsai.shared.Money;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Um voo da reserva. Os dados do voo são cópia do catálogo no momento do hold. */
@Entity
@Table(name = "booking_items")
class BookingItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "flight_id", nullable = false)
    private UUID flightId;

    @Column(name = "flight_number", nullable = false)
    private String flightNumber;

    @Column(name = "origin_code", nullable = false)
    private String originCode;

    @Column(name = "destination_code", nullable = false)
    private String destinationCode;

    @Column(name = "departure_at", nullable = false)
    private Instant departureAt;

    @Column(name = "fare_class_code", nullable = false)
    private String fareClassCode;

    @Column(nullable = false)
    private int seats;

    @Column(name = "unit_price_amount", nullable = false)
    private BigDecimal unitPriceAmount;

    @Column(nullable = false)
    private String currency;

    protected BookingItem() {
    }

    BookingItem(Booking booking, FareAvailability fare, int seats) {
        this.id = UUID.randomUUID();
        this.booking = booking;
        this.flightId = fare.flightId();
        this.flightNumber = fare.flightNumber();
        this.originCode = fare.origin();
        this.destinationCode = fare.destination();
        this.departureAt = fare.departureAt();
        this.fareClassCode = fare.fareClass();
        this.seats = seats;
        this.unitPriceAmount = fare.price().amount();
        this.currency = fare.price().currency();
    }

    UUID getFlightId() {
        return flightId;
    }

    String getFareClassCode() {
        return fareClassCode;
    }

    int getSeats() {
        return seats;
    }

    BookingView.Item toView() {
        return new BookingView.Item(flightId, flightNumber, originCode, destinationCode, departureAt,
                fareClassCode, seats, new Money(unitPriceAmount, currency));
    }
}
