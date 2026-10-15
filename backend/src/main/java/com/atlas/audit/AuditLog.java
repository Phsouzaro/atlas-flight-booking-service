package com.atlas.audit;

/** Trilha de auditoria. */
public interface AuditLog {

    /**
     * Grava a entrada na transação corrente.
     *
     * @throws org.springframework.transaction.IllegalTransactionStateException se não houver
     *         transação aberta: auditoria fora da transação da escrita poderia sobrar ou faltar
     */
    void record(AuditEntry entry);
}
