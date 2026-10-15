/**
 * Ações propostas que só executam quando o usuário confirma (ADR-002).
 * <p>
 * API pública (este pacote): {@link com.flightsai.pendingaction.PendingActions} para propor e
 * consultar, e {@link com.flightsai.pendingaction.PendingActionHandler}, que cada módulo implementa
 * para executar os seus tipos de ação. Este módulo não conhece quem executa: despacha pelo tipo, o que
 * evita o ciclo "booking propõe, pendingaction executa no booking".
 * <p>
 * A execução só acontece por {@code POST /pending-actions/{id}/confirm}, chamado pela UI. A IA propõe,
 * nunca confirma.
 */
@ApplicationModule(displayName = "Pending actions")
package com.flightsai.pendingaction;

import org.springframework.modulith.ApplicationModule;
