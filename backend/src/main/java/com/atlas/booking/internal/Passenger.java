package com.atlas.booking.internal;

import java.time.LocalDate;
import java.util.UUID;

import com.atlas.booking.PassengerInfo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "passengers")
class Passenger {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    /** Ordem em que o passageiro foi informado. */
    @Column(nullable = false)
    private int position;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "document_number", nullable = false)
    private String documentNumber;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    protected Passenger() {
    }

    Passenger(Booking booking, int position, PassengerInfo info) {
        this.id = UUID.randomUUID();
        this.booking = booking;
        this.position = position;
        this.fullName = info.fullName().trim();
        this.documentNumber = info.documentNumber();
        this.birthDate = info.birthDate();
    }

    PassengerInfo toView() {
        return new PassengerInfo(fullName, documentNumber, birthDate);
    }
}
