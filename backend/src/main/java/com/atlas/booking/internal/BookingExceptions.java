package com.atlas.booking.internal;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.atlas.booking.BookingRejectedException;

/** Também quando a reserva existe mas é de outro usuário: não revela que ela existe. */
class BookingNotFoundException extends RuntimeException {
    BookingNotFoundException(UUID id) {
        super("Booking not found: " + id);
    }
}

/** Erros do módulo em application/problem+json (RFC 9457), com o motivo em {@code reason}. */
@RestControllerAdvice
class BookingExceptionHandler {

    @ExceptionHandler(BookingNotFoundException.class)
    ProblemDetail onNotFound(BookingNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Not Found");
        return problem;
    }

    @ExceptionHandler(BookingRejectedException.class)
    ProblemDetail onRejected(BookingRejectedException ex) {
        HttpStatus status = switch (ex.reason()) {
            case FARE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case PAYMENT_DECLINED -> HttpStatus.PAYMENT_REQUIRED;
            case PAYMENT_REQUIRED, REBOOKING_NOT_ALLOWED -> HttpStatus.UNPROCESSABLE_ENTITY;
            case FLIGHT_DEPARTED, SEATS_UNAVAILABLE, INVALID_STATE, HOLD_EXPIRED, QUOTE_CHANGED -> HttpStatus.CONFLICT;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("reason", ex.reason().name());
        return problem;
    }
}
