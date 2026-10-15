-- Respostas guardadas por Idempotency-Key (shared.idempotency). A chave vale por usuário: dois usuários
-- podem mandar a mesma string sem colidir.
CREATE TABLE idempotency_keys (
    user_id         UUID         NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    operation       VARCHAR(60)  NOT NULL,
    request_hash    VARCHAR(64)  NOT NULL,  -- SHA-256 em hex de operação + corpo
    response_status INT          NOT NULL,
    response_body   JSONB        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (user_id, idempotency_key)
);
