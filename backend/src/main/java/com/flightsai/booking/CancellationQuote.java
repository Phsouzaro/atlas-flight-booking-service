package com.flightsai.booking;

import java.time.Instant;
import java.util.UUID;

import com.flightsai.shared.Money;

/**
 * Cotação de cancelamento. Não tem efeito: só vale depois que o usuário confirma a ação pendente.
 *
 * @param paid       quanto a reserva custou
 * @param retained   quanto fica retido (multa ou tarifa não reembolsável)
 * @param refund     quanto volta para o usuário
 * @param freeWindow cancelamento dentro das 24h da compra, sem custo
 * @param rule       explicação curta da regra aplicada
 */
public record CancellationQuote(
        UUID bookingId,
        String bookingCode,
        Money paid,
        Money retained,
        Money refund,
        boolean freeWindow,
        String rule,
        UUID pendingActionId,
        Instant expiresAt) {
}
