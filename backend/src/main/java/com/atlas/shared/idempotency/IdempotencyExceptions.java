package com.atlas.shared.idempotency;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Erros de Idempotency-Key em application/problem+json (RFC 9457). */
@RestControllerAdvice
class IdempotencyExceptionHandler {

    @ExceptionHandler(InvalidIdempotencyKeyException.class)
    ProblemDetail onInvalidKey(InvalidIdempotencyKeyException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Bad Request");
        return problem;
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    ProblemDetail onKeyReused(IdempotencyKeyReusedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Unprocessable Entity");
        return problem;
    }
}
