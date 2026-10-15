package com.atlas.booking.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.atlas.booking.internal.FarePolicy.CancellationTerms;
import com.atlas.booking.internal.FarePolicy.RebookingTerms;
import com.atlas.catalog.FareRules;
import com.atlas.shared.Money;

/** Regras puras, sem Spring nem banco. */
class FarePolicyTest {

    static final Instant PURCHASED = Instant.parse("2026-03-10T12:00:00Z");
    static final Instant FAR_DEPARTURE = PURCHASED.plus(Duration.ofDays(15));

    static final FareRules LIGHT = new FareRules("ECONOMY_LIGHT", false, brl("0"), brl("250.00"));
    static final FareRules FLEX = new FareRules("ECONOMY_FLEX", true, brl("150.00"), brl("0"));
    static final FareRules BUSINESS = new FareRules("BUSINESS", true, brl("0"), brl("0"));

    @Test
    void freeWindowRefundsEverythingUpTo24hAfterPurchase() {
        CancellationTerms almost = FarePolicy.cancellation(brl("399.90"), 1, FAR_DEPARTURE, PURCHASED, LIGHT,
                PURCHASED.plus(Duration.ofHours(24)).minusSeconds(1));
        CancellationTerms after = FarePolicy.cancellation(brl("399.90"), 1, FAR_DEPARTURE, PURCHASED, LIGHT,
                PURCHASED.plus(Duration.ofHours(24)));

        assertThat(almost.freeWindow()).isTrue();
        assertThat(almost.refund()).isEqualTo(brl("399.90"));
        assertThat(after.freeWindow()).isFalse();
        assertThat(after.refund().amount()).isEqualByComparingTo("0");
    }

    @Test
    void freeWindowNeedsSevenDaysOfAdvance() {
        Instant now = PURCHASED.plusSeconds(60);

        assertThat(FarePolicy.cancellation(brl("399.90"), 1, PURCHASED.plus(Duration.ofDays(7)), PURCHASED, LIGHT, now)
                .freeWindow()).isTrue();
        assertThat(FarePolicy.cancellation(brl("399.90"), 1, PURCHASED.plus(Duration.ofDays(7)).minusSeconds(1),
                PURCHASED, LIGHT, now).freeWindow()).isFalse();
    }

    @Test
    void noFreeWindowWithoutAnOriginalPurchase() {
        CancellationTerms terms = FarePolicy.cancellation(brl("399.90"), 1, FAR_DEPARTURE, null, LIGHT, PURCHASED);

        assertThat(terms.freeWindow()).isFalse();
    }

    @Test
    void appliesClassRulesOutsideTheWindow() {
        Instant late = PURCHASED.plus(Duration.ofDays(2));

        CancellationTerms light = FarePolicy.cancellation(brl("799.80"), 2, FAR_DEPARTURE, PURCHASED, LIGHT, late);
        assertThat(light.refund().amount()).isEqualByComparingTo("0");
        assertThat(light.retained().amount()).isEqualByComparingTo("799.80");

        CancellationTerms flex = FarePolicy.cancellation(brl("1119.80"), 2, FAR_DEPARTURE, PURCHASED, FLEX, late);
        assertThat(flex.refund().amount()).isEqualByComparingTo("819.80");
        assertThat(flex.retained().amount()).isEqualByComparingTo("300.00");

        CancellationTerms business = FarePolicy.cancellation(brl("1279.90"), 1, FAR_DEPARTURE, PURCHASED, BUSINESS, late);
        assertThat(business.refund().amount()).isEqualByComparingTo("1279.90");
    }

    @Test
    void cancellationFeeNeverMakesRefundNegative() {
        FareRules steep = new FareRules("ECONOMY_FLEX", true, brl("500.00"), brl("0"));

        CancellationTerms terms = FarePolicy.cancellation(brl("399.90"), 1, FAR_DEPARTURE, PURCHASED, steep,
                PURCHASED.plus(Duration.ofDays(2)));

        assertThat(terms.refund().amount()).isEqualByComparingTo("0");
        assertThat(terms.retained().amount()).isEqualByComparingTo("399.90");
    }

    @Test
    void rebookingChargesFeeOfCurrentFareAndOnlyPositiveDifference() {
        RebookingTerms sameFare = FarePolicy.rebooking(brl("399.90"), brl("399.90"), 2, LIGHT);
        assertThat(sameFare.changeFee().amount()).isEqualByComparingTo("500.00");
        assertThat(sameFare.amountDue().amount()).isEqualByComparingTo("500.00");

        RebookingTerms upgrade = FarePolicy.rebooking(brl("559.90"), brl("1279.90"), 1, FLEX);
        assertThat(upgrade.fareDifference().amount()).isEqualByComparingTo("720.00");
        assertThat(upgrade.amountDue().amount()).isEqualByComparingTo("720.00");

        RebookingTerms downgrade = FarePolicy.rebooking(brl("1279.90"), brl("399.90"), 1, BUSINESS);
        assertThat(downgrade.fareDifference().amount()).isEqualByComparingTo("-880.00");
        assertThat(downgrade.amountDue().amount()).isEqualByComparingTo("0");
    }

    private static Money brl(String amount) {
        return new Money(new BigDecimal(amount), "BRL");
    }
}
