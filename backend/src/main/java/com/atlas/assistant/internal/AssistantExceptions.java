package com.atlas.assistant.internal;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Também quando a conversa é de outro usuário. */
class ConversationNotFoundException extends RuntimeException {
    ConversationNotFoundException(UUID id) {
        super("Conversation not found: " + id);
    }
}

class InvalidAssistantInputException extends RuntimeException {
    InvalidAssistantInputException(String message) {
        super(message);
    }
}

/** Mesma Idempotency-Key de um turno que ainda não terminou. */
class TurnInProgressException extends RuntimeException {
    TurnInProgressException() {
        super("A message with this Idempotency-Key is still being processed");
    }
}

/** Sem chave da OpenAI ou o modelo falhou. A mensagem nunca leva detalhe do erro de fora. */
class AssistantUnavailableException extends RuntimeException {
    AssistantUnavailableException(String message) {
        super(message);
    }
}

/** Erros do módulo em application/problem+json (RFC 9457). */
@RestControllerAdvice
class AssistantExceptionHandler {

    @ExceptionHandler(ConversationNotFoundException.class)
    ProblemDetail onNotFound(ConversationNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InvalidAssistantInputException.class)
    ProblemDetail onInvalid(InvalidAssistantInputException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(TurnInProgressException.class)
    ProblemDetail onInProgress(TurnInProgressException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(AssistantUnavailableException.class)
    ProblemDetail onUnavailable(AssistantUnavailableException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        return problem;
    }
}
