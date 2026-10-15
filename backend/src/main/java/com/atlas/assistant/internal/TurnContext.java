package com.atlas.assistant.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Estado de um turno do assistente: a conversa e o que as tools fizeram. As tools são singletons e
 * rodam na mesma thread da requisição, então o estado vai numa ThreadLocal aberta e fechada pelo
 * {@link AssistantService}.
 */
final class TurnContext {

    private static final ThreadLocal<TurnContext> CURRENT = new ThreadLocal<>();

    private final UUID conversationId;
    private final List<String> toolCalls = new ArrayList<>();
    private final List<UUID> pendingActionIds = new ArrayList<>();
    private final StringBuilder toolResults = new StringBuilder();

    private TurnContext(UUID conversationId) {
        this.conversationId = conversationId;
    }

    static TurnContext open(UUID conversationId) {
        TurnContext turn = new TurnContext(conversationId);
        CURRENT.set(turn);
        return turn;
    }

    static void close() {
        CURRENT.remove();
    }

    static TurnContext current() {
        TurnContext turn = CURRENT.get();
        if (turn == null) {
            throw new IllegalStateException("Assistant tool called outside of a turn");
        }
        return turn;
    }

    UUID conversationId() {
        return conversationId;
    }

    void recordToolCall(String name) {
        toolCalls.add(name);
    }

    void recordPendingAction(UUID id) {
        pendingActionIds.add(id);
    }

    void recordToolResult(String name, String result) {
        toolResults.append(name).append(": ").append(result).append('\n');
    }

    /** Resultados das tools do turno, na ordem; vazio se nenhuma rodou. */
    String toolResults() {
        return toolResults.toString();
    }

    List<String> toolCalls() {
        return List.copyOf(toolCalls);
    }

    List<UUID> pendingActionIds() {
        return List.copyOf(pendingActionIds);
    }
}
