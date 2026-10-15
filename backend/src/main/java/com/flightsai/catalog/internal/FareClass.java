package com.flightsai.catalog.internal;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Dado de referência, vem da migration V3. */
@Entity
@Immutable
@Table(name = "fare_classes")
class FareClass {

    @Id
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Cabin cabin;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected FareClass() {
    }

    String getCode() {
        return code;
    }

    String getName() {
        return name;
    }

    Cabin getCabin() {
        return cabin;
    }

    int getDisplayOrder() {
        return displayOrder;
    }
}
