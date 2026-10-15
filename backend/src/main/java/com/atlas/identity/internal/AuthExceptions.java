package com.atlas.identity.internal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

class EmailAlreadyRegisteredException extends RuntimeException {
    EmailAlreadyRegisteredException() {
        super("Email already registered");
    }
}

class InvalidCredentialsException extends RuntimeException {
    InvalidCredentialsException() {
        super("Invalid credentials");
    }
}

/** Erros do módulo em application/problem+json (RFC 9457). */
@RestControllerAdvice
class AuthExceptionHandler {

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    ProblemDetail onEmailTaken(EmailAlreadyRegisteredException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Conflict");
        return problem;
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail onInvalidCredentials(InvalidCredentialsException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle("Unauthorized");
        return problem;
    }
}
