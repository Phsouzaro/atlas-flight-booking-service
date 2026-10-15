package com.flightsai.catalog.internal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "flights")
class Flight {

    @Id
    private UUID id;

    @Column(name = "flight_number", nullable = false)
    private String flightNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "origin_code", nullable = false)
    private Airport origin;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_code", nullable = false)
    private Airport destination;

    @Column(name = "departure_at", nullable = false)
    private Instant departureAt;

    @Column(name = "arrival_at", nullable = false)
    private Instant arrivalAt;

    /** Lazy por padrão; as consultas que precisam carregam junto pelo entity graph. */
    @OneToMany(mappedBy = "flight")
    private List<SeatInventory> seats = new ArrayList<>();

    protected Flight() {
    }

    UUID getId() {
        return id;
    }

    String getFlightNumber() {
        return flightNumber;
    }

    Airport getOrigin() {
        return origin;
    }

    Airport getDestination() {
        return destination;
    }

    Instant getDepartureAt() {
        return departureAt;
    }

    Instant getArrivalAt() {
        return arrivalAt;
    }

    List<SeatInventory> getSeats() {
        return seats;
    }
}
