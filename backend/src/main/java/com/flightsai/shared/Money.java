package com.flightsai.shared;

import java.math.BigDecimal;
import java.util.Objects;

/** Valor monetário com o código da moeda (ISO 4217). Dinheiro nunca é {@code double}. */
public record Money(BigDecimal amount, String currency) {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Invalid currency code: " + currency);
        }
    }
}
