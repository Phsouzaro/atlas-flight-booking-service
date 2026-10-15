package com.flightsai.booking.internal;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import com.flightsai.catalog.FareRules;
import com.flightsai.shared.Money;

/**
 * Regras de cancelamento e remarcação. Funções puras: recebem o estado e o relógio, devolvem os
 * valores. A mesma função cota e revalida, então a confirmação nunca usa uma regra diferente da cotação.
 */
final class FarePolicy {

    /** Desistência sem custo até 24h depois da compra... */
    static final Duration FREE_WINDOW = Duration.ofHours(24);
    /** ...se a compra foi feita com pelo menos 7 dias de antecedência do voo (inspirada na regra da ANAC). */
    static final Duration FREE_WINDOW_MIN_ADVANCE = Duration.ofDays(7);

    private FarePolicy() {
    }

    record CancellationTerms(Money paid, Money retained, Money refund, boolean freeWindow, String rule) {
    }

    record RebookingTerms(Money changeFee, Money fareDifference, Money amountDue) {
    }

    /**
     * @param purchasedAt quando a reserva foi paga; {@code null} se ela veio de uma remarcação (a janela
     *                    de 24h vale só para a compra original)
     */
    static CancellationTerms cancellation(Money paid, int seats, Instant departureAt, Instant purchasedAt,
                                          FareRules rules, Instant now) {
        boolean freeWindow = purchasedAt != null
                && now.isBefore(purchasedAt.plus(FREE_WINDOW))
                && !departureAt.isBefore(purchasedAt.plus(FREE_WINDOW_MIN_ADVANCE));
        if (freeWindow) {
            return new CancellationTerms(paid, zero(paid), paid, true,
                    "Cancelamento em até 24h da compra, feita com 7 dias ou mais de antecedência: reembolso integral");
        }
        if (!rules.refundable()) {
            return new CancellationTerms(paid, paid, zero(paid), false,
                    "Tarifa " + rules.fareClass() + " não é reembolsável");
        }
        Money fee = times(sameCurrency(rules.cancellationFee(), paid), seats);
        BigDecimal refund = paid.amount().subtract(fee.amount()).max(BigDecimal.ZERO);
        Money refundMoney = new Money(refund, paid.currency());
        return new CancellationTerms(paid, new Money(paid.amount().subtract(refund), paid.currency()), refundMoney, false,
                "Tarifa " + rules.fareClass() + " reembolsável com multa de " + rules.cancellationFee().amount()
                        + " " + rules.cancellationFee().currency() + " por passageiro");
    }

    /**
     * @param currentUnitPrice preço por passageiro da tarifa atual
     * @param targetUnitPrice  preço por passageiro da tarifa de destino, como está no catálogo agora
     * @param currentRules     regras da tarifa atual (a multa é dela, não da nova)
     */
    static RebookingTerms rebooking(Money currentUnitPrice, Money targetUnitPrice, int seats, FareRules currentRules) {
        Money changeFee = times(sameCurrency(currentRules.changeFee(), currentUnitPrice), seats);
        Money difference = times(new Money(
                sameCurrency(targetUnitPrice, currentUnitPrice).amount().subtract(currentUnitPrice.amount()),
                currentUnitPrice.currency()), seats);
        BigDecimal due = changeFee.amount().add(difference.amount().max(BigDecimal.ZERO));
        return new RebookingTerms(changeFee, difference, new Money(due, currentUnitPrice.currency()));
    }

    private static Money times(Money money, int seats) {
        return new Money(money.amount().multiply(BigDecimal.valueOf(seats)), money.currency());
    }

    private static Money zero(Money like) {
        return new Money(BigDecimal.ZERO.setScale(like.amount().scale()), like.currency());
    }

    /** O catálogo simulado é todo em BRL; misturar moedas aqui é bug, não regra de câmbio. */
    private static Money sameCurrency(Money value, Money reference) {
        if (!value.currency().equals(reference.currency())) {
            throw new IllegalStateException("Currency mismatch: " + value.currency() + " vs " + reference.currency());
        }
        return value;
    }
}
