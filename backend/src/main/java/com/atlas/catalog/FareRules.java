package com.atlas.catalog;

import com.atlas.shared.Money;

/**
 * Regras de uma classe tarifária. Multas são por passageiro.
 *
 * @param refundable      se o cancelamento devolve dinheiro fora da janela de 24h
 * @param cancellationFee multa descontada do reembolso, quando reembolsável
 * @param changeFee       multa de remarcação
 */
public record FareRules(String fareClass, boolean refundable, Money cancellationFee, Money changeFee) {
}
