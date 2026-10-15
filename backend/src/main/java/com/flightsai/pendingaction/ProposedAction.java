package com.flightsai.pendingaction;

import java.util.Objects;

/**
 * Uma proposta.
 *
 * @param type    tipo da ação; precisa existir um {@link PendingActionHandler} com esse tipo
 * @param summary texto curto para o card de confirmação, ex.: Cancelar a reserva Q78VHS, reembolso de BRL 0.00
 * @param payload o que foi cotado (serializado em JSON). O handler recalcula na confirmação e compara.
 */
public record ProposedAction(String type, String summary, Object payload) {

    public ProposedAction {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(payload, "payload");
    }
}
