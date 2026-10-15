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

/** Refresh token ausente, desconhecido, vencido ou revogado: o cliente precisa entrar de novo. */
class InvalidRefreshTokenException extends RuntimeException {
    InvalidRefreshTokenException() {
        super("Session expired; log in again");
    }
}

/** O token acabou de ser trocado por outra aba: o cookie já tem o novo, basta repetir o refresh. */
class RefreshRaceException extends RuntimeException {
    RefreshRaceException() {
        super("Session was just refreshed by another request; retry");
    }
}

/** Erros do módulo em application/problem+json (RFC 9457). */
@RestControllerAdvice
class AuthExceptionHandler {

    @ExceptionHandler(InvalidRefreshTokenException.class)
    ProblemDetail onInvalidRefresh(InvalidRefreshTokenException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle("Unauthorized");
        problem.setProperty("reason", "INVALID_REFRESH_TOKEN");
        return problem;
    }

    @ExceptionHandler(RefreshRaceException.class)
    ProblemDetail onRefreshRace(RefreshRaceException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Conflict");
        problem.setProperty("reason", "REFRESH_RACE");
        return problem;
    }

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
