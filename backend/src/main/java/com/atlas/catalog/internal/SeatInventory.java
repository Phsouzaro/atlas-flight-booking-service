package com.atlas.catalog.internal;

import java.math.BigDecimal;
import java.util.UUID;

import com.atlas.shared.Money;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Assentos e preço de uma classe tarifária em um voo. */
@Entity
@Table(name = "seat_inventory")
class SeatInventory {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flight_id", nullable = false)
    private Flight flight;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fare_class_code", nullable = false)
    private FareClass fareClass;

    @Column(name = "total_seats", nullable = false)
    private int totalSeats;

    @Column(name = "available_seats", nullable = false)
    private int availableSeats;

    @Column(name = "price_amount", nullable = false)
    private BigDecimal priceAmount;

    @Column(nullable = false)
    private String currency;

    protected SeatInventory() {
    }

    Flight getFlight() {
        return flight;
    }

    FareClass getFareClass() {
        return fareClass;
    }

    int getTotalSeats() {
        return totalSeats;
    }

    int getAvailableSeats() {
        return availableSeats;
    }

    Money price() {
        return new Money(priceAmount, currency);
    }
}
