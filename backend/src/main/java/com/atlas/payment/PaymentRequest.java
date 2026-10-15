package com.atlas.payment;

import java.util.Objects;
import java.util.UUID;

import com.atlas.shared.Money;

/**
 * Pedido de cobrança.
 *
 * @param bookingId reserva cobrada, para rastreio
 * @param amount    valor total
 * @param cardToken token do cartão gerado pelo front (nunca o número do cartão)
 */
public record PaymentRequest(UUID bookingId, Money amount, String cardToken) {

    public PaymentRequest {
        Objects.requireNonNull(bookingId, "bookingId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(cardToken, "cardToken");
    }
}
