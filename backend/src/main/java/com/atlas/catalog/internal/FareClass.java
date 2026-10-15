package com.atlas.catalog.internal;

import java.math.BigDecimal;

import org.hibernate.annotations.Immutable;

import com.atlas.catalog.FareRules;
import com.atlas.shared.Money;

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

    @Column(nullable = false)
    private boolean refundable;

    @Column(name = "cancellation_fee", nullable = false)
    private BigDecimal cancellationFee;

    @Column(name = "change_fee", nullable = false)
    private BigDecimal changeFee;

    @Column(name = "fee_currency", nullable = false)
    private String feeCurrency;

    protected FareClass() {
    }

    FareRules toRules() {
        return new FareRules(code, refundable, new Money(cancellationFee, feeCurrency), new Money(changeFee, feeCurrency));
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
