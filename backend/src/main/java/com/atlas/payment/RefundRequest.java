package com.atlas.payment;

import java.util.Objects;
import java.util.UUID;

import com.atlas.shared.Money;

/**
 * Pedido de estorno.
 *
 * @param paymentReference referência da cobrança original
 * @param amount           valor a devolver, maior que zero
 */
public record RefundRequest(UUID bookingId, String paymentReference, Money amount) {

    public RefundRequest {
        Objects.requireNonNull(bookingId, "bookingId");
        Objects.requireNonNull(paymentReference, "paymentReference");
        Objects.requireNonNull(amount, "amount");
        if (amount.amount().signum() <= 0) {
            throw new IllegalArgumentException("Refund amount must be positive");
        }
    }
}
