package com.flightsai.pendingaction;

import java.util.Map;
import java.util.UUID;

/**
 * O que o handler recebe na confirmação.
 *
 * @param userId    dono da ação, vindo do token de quem confirmou
 * @param payload   o que foi cotado, como JSON genérico
 * @param cardToken token do cartão, quando a ação precisa cobrar (ex.: diferença de remarcação); pode ser null
 */
public record PendingActionExecution(UUID actionId, UUID userId, Map<String, Object> payload, String cardToken) {
}
