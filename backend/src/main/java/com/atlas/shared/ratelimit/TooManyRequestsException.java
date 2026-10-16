package com.atlas.shared.ratelimit;

import java.time.Duration;

/** Vira 429 com {@code Retry-After}. A mensagem é segura para mostrar ao cliente. */
public class TooManyRequestsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyRequestsException(Duration retryAfter) {
        super("Too many requests; try again later");
        this.retryAfter = retryAfter;
    }

    /** Segundos inteiros, arredondados para cima: nunca mandar o cliente voltar antes da hora. */
    public long retryAfterSeconds() {
        return Math.max(1, (retryAfter.toMillis() + 999) / 1000);
    }
}
