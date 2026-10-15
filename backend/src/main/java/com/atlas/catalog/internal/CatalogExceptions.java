package com.atlas.catalog.internal;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.atlas.catalog.InvalidFlightSearchException;

class FlightNotFoundException extends RuntimeException {
    FlightNotFoundException(UUID id) {
        super("Flight not found: " + id);
    }
}

/** Erros do módulo em application/problem+json (RFC 9457). */
@RestControllerAdvice
class CatalogExceptionHandler {

    @ExceptionHandler(InvalidFlightSearchException.class)
    ProblemDetail onInvalidSearch(InvalidFlightSearchException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Bad Request");
        return problem;
    }

    @ExceptionHandler(FlightNotFoundException.class)
    ProblemDetail onFlightNotFound(FlightNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Not Found");
        return problem;
    }
}
