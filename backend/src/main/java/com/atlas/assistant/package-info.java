/**
 * Assistente de reservas em linguagem natural (fase 4).
 * <p>
 * O modelo só consulta e propõe (ADR-002): as tools chamam as APIs públicas de catalog e booking, e
 * nenhuma delas paga, cancela ou remarca. Cancelar e remarcar viram uma {@code PendingAction} que o
 * usuário confirma pela UI. Decisões de desenho no ADR-005.
 * <p>
 * Não expõe API pública: só os endpoints REST em {@code assistant.internal}.
 */
@ApplicationModule(displayName = "Assistant")
package com.atlas.assistant;

import org.springframework.modulith.ApplicationModule;
