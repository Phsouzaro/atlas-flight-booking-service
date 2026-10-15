package com.atlas.pendingaction;

/** {@code EXPIRED} não é gravado: é um {@code PENDING} cujo prazo já passou. */
public enum PendingActionStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
    EXPIRED
}
