package com.flightsai.payment;

/** Cobrança de uma reserva. */
public interface PaymentGateway {

    /** Recusa vem como {@link PaymentResult} com {@code approved = false}, não como exceção. */
    PaymentResult charge(PaymentRequest request);
}
