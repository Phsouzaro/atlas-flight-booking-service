package com.atlas.booking;

import java.time.Instant;
import java.util.UUID;

import com.atlas.shared.Money;

/**
 * Proposta de remarcação. Não tem efeito: só vale depois que o usuário confirma a ação pendente.
 *
 * @param changeFee      multa de remarcação (todos os passageiros)
 * @param fareDifference diferença de tarifa (todos os passageiros); negativa quando o voo novo é mais
 *                       barato, mas a diferença negativa não é devolvida
 * @param amountDue      quanto será cobrado na confirmação: multa mais a diferença, se positiva
 */
public record RebookingProposal(
        UUID bookingId,
        String bookingCode,
        UUID newFlightId,
        String newFlightNumber,
        Instant newDepartureAt,
        String newFareClass,
        int seats,
        Money changeFee,
        Money fareDifference,
        Money amountDue,
        UUID pendingActionId,
        Instant expiresAt) {
}
