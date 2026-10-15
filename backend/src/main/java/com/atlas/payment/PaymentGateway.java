package com.atlas.payment;

/** Cobrança e estorno de uma reserva. */
public interface PaymentGateway {

    /** Recusa vem como {@link PaymentResult} com {@code approved = false}, não como exceção. */
    PaymentResult charge(PaymentRequest request);

    /**
     * Devolve {@code amount} de uma cobrança já aprovada.
     *
     * @return a referência do estorno
     */
    String refund(RefundRequest request);
}
