package com.atlas.pendingaction;

/**
 * Executa um tipo de ação quando o usuário confirma. Implementado pelo módulo dono da ação (ex.:
 * booking para cancelar e remarcar) e registrado como bean.
 * <p>
 * Roda dentro da transação da confirmação, com a ação já travada. Deve revalidar tudo a partir do
 * estado atual e não confiar no payload cotado. Se lançar exceção, nada é gravado e a ação continua
 * pendente.
 */
public interface PendingActionHandler {

    /** Tipo atendido. Único na aplicação. */
    String type();

    /** @return o resultado da execução, guardado na ação e devolvido ao cliente */
    Object execute(PendingActionExecution execution);
}
