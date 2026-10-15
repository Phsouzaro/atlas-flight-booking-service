-- Refresh tokens (módulo identity, fase 6). Só o hash SHA-256 é guardado: quem lê o banco não consegue
-- usar o token. Cada uso troca o token (rotação); os tokens de um mesmo login formam uma família, e
-- reapresentar um token já trocado revoga a família inteira (sinal de roubo).
CREATE TABLE refresh_tokens (
    id            UUID        PRIMARY KEY,
    user_id       UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id     UUID        NOT NULL,
    token_hash    VARCHAR(64) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    expires_at    TIMESTAMPTZ NOT NULL,   -- janela de inatividade; anda a cada rotação
    family_expires_at TIMESTAMPTZ NOT NULL, -- teto da sessão desde o login; não anda
    revoked_at    TIMESTAMPTZ,
    replaced_by   UUID REFERENCES refresh_tokens (id),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_refresh_tokens_expiry CHECK (expires_at <= family_expires_at)
);

CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX ix_refresh_tokens_user_active ON refresh_tokens (user_id) WHERE revoked_at IS NULL;
