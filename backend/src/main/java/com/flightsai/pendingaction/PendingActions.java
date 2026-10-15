package com.flightsai.pendingaction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Ações pendentes do usuário autenticado ({@code identity.CurrentUser}). */
public interface PendingActions {

    /**
     * Registra uma proposta para o usuário confirmar ou rejeitar. Não executa nada. Participa da
     * transação de quem chama.
     */
    PendingActionView propose(ProposedAction proposal);

    /** Da mais recente para a mais antiga. */
    List<PendingActionView> myActions();

    Optional<PendingActionView> findMine(UUID actionId);
}
