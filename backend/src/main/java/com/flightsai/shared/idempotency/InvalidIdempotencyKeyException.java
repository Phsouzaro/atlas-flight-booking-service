package com.flightsai.shared.idempotency;

public class InvalidIdempotencyKeyException extends RuntimeException {

    public InvalidIdempotencyKeyException() {
        super("Idempotency-Key must have 1 to " + IdempotentExecutor.MAX_KEY_LENGTH
                + " visible ASCII characters");
    }
}
