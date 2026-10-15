package com.atlas.assistant.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.atlas.pendingaction.PendingActionView;

import jakarta.validation.constraints.NotBlank;

record ConversationView(UUID id, Instant createdAt, Instant updatedAt) {
}

/** {@code content} já mascarado: é o que o modelo viu. {@code toolContext} fica só no backend. */
record MessageView(UUID id, String role, String content, Instant createdAt, List<String> toolCalls,
                   List<UUID> pendingActionIds, Attachments attachments, @JsonIgnore String toolContext) {
}

/**
 * Voos e reservas que as tools devolveram no turno, para a UI desenhar cards. São os mesmos dados que o
 * modelo recebeu, sem PII além do primeiro nome e inicial do passageiro.
 */
record Attachments(List<ToolViews.FlightInfo> flights, List<ToolViews.BookingInfo> bookings) {

    static final Attachments EMPTY = new Attachments(List.of(), List.of());

    Attachments {
        flights = flights == null ? List.of() : List.copyOf(flights);
        bookings = bookings == null ? List.of() : List.copyOf(bookings);
    }
}

record SendMessageRequest(@NotBlank String content) {
}

/**
 * Resposta de um turno.
 *
 * @param toolCalls      tools que o modelo chamou, na ordem; {@code blocked:nome} quando pediu uma que não existe
 * @param pendingActions propostas criadas neste turno; a UI mostra um card com Confirmar e Rejeitar para cada uma
 * @param attachments    voos e reservas que as tools devolveram, para os cards
 */
record AssistantReply(UUID conversationId, UUID messageId, String content, List<String> toolCalls,
                      List<PendingActionView> pendingActions, Attachments attachments, String promptVersion,
                      TokenUsage usage) {
}

record TokenUsage(int promptTokens, int completionTokens) {
}
