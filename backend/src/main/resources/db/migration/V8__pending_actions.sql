-- Ações propostas que só executam depois que o usuário confirma (módulo pendingaction, ADR-002).
-- payload guarda o que foi cotado; na confirmação o backend recalcula e compara, nunca confia nele.
CREATE TABLE pending_actions (
    id          UUID         PRIMARY KEY,
    user_id     UUID         NOT NULL,
    type        VARCHAR(40)  NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    summary     VARCHAR(300) NOT NULL,   -- texto curto para o card de confirmação
    payload     JSONB        NOT NULL,
    result      JSONB,                   -- resposta da execução, quando CONFIRMED
    created_at  TIMESTAMPTZ  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    decided_at  TIMESTAMPTZ,
    CONSTRAINT ck_pending_actions_status CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED')),
    CONSTRAINT ck_pending_actions_decided CHECK ((status = 'PENDING') = (decided_at IS NULL))
);

CREATE INDEX ix_pending_actions_user ON pending_actions (user_id, created_at DESC);
