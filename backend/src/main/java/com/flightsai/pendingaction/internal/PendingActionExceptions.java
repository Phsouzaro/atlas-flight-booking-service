package com.flightsai.pendingaction.internal;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Também quando a ação é de outro usuário. */
class PendingActionNotFoundException extends RuntimeException {
    PendingActionNotFoundException(UUID id) {
        super("Pending action not found: " + id);
    }
}

/** Ação já confirmada, rejeitada ou expirada. */
class PendingActionClosedException extends RuntimeException {
    PendingActionClosedException(String message) {
        super(message);
    }
}

/** Erros do módulo em application/problem+json (RFC 9457). */
@RestControllerAdvice
class PendingActionExceptionHandler {

    @ExceptionHandler(PendingActionNotFoundException.class)
    ProblemDetail onNotFound(PendingActionNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Not Found");
        return problem;
    }

    @ExceptionHandler(PendingActionClosedException.class)
    ProblemDetail onClosed(PendingActionClosedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Conflict");
        problem.setProperty("reason", "ACTION_CLOSED");
        return problem;
    }
}
