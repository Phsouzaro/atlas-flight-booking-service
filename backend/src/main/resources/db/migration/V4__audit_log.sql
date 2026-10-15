-- Trilha de auditoria (módulo audit). Uma linha por escrita de negócio, gravada na mesma transação.
-- details nunca leva PII (nome, documento de passageiro); só ids, valores e estados.
CREATE TABLE audit_log (
    id            UUID        PRIMARY KEY,
    occurred_at   TIMESTAMPTZ NOT NULL,
    actor_user_id UUID,                     -- null quando é o sistema (ex.: job de expiração)
    action        VARCHAR(60) NOT NULL,
    entity_type   VARCHAR(40) NOT NULL,
    entity_id     VARCHAR(64) NOT NULL,
    details       JSONB       NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX ix_audit_log_entity ON audit_log (entity_type, entity_id, occurred_at);
