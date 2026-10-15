package com.atlas.assistant.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.atlas.shared.idempotency.IdempotentExecutor;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/assistant/conversations")
class AssistantController {

    private final AssistantService assistant;

    AssistantController(AssistantService assistant) {
        this.assistant = assistant;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ConversationView start() {
        return assistant.startConversation();
    }

    @GetMapping
    List<ConversationView> myConversations() {
        return assistant.myConversations();
    }

    @GetMapping("/{id}/messages")
    List<MessageView> messages(@PathVariable UUID id) {
        return assistant.messages(id);
    }

    /**
     * Manda uma mensagem e devolve a resposta. A Idempotency-Key vale por conversa e é tratada aqui, não
     * pelo {@link IdempotentExecutor}: aquele roda tudo numa transação, e um turno leva segundos chamando o
     * modelo. Repetir a mesma chave devolve a mesma resposta sem chamar o modelo de novo.
     */
    @PostMapping("/{id}/messages")
    ResponseEntity<AssistantReply> send(@RequestHeader(IdempotentExecutor.HEADER) String idempotencyKey,
                                        @PathVariable UUID id,
                                        @Valid @RequestBody SendMessageRequest request) {
        if (idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new InvalidAssistantInputException("Idempotency-Key must have 1 to 100 characters");
        }
        AssistantService.Turn turn = assistant.send(id, request.content(), idempotencyKey);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (turn.replayed()) {
            response.header(IdempotentExecutor.REPLAYED_HEADER, "true");
        }
        return response.body(turn.reply());
    }
}
