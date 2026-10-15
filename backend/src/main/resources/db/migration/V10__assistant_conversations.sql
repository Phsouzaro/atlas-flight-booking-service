-- Memória de conversa do assistente (módulo assistant, fase 4). Cada conversa tem dono; o backend só
-- carrega mensagens de conversas do usuário do token.
-- content guarda o texto já mascarado: documento, email e telefone viram [DOC_1], [EMAIL_1]...

CREATE TABLE assistant_conversations (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_assistant_conversations_user ON assistant_conversations (user_id, updated_at DESC);

CREATE TABLE assistant_messages (
    id                UUID        PRIMARY KEY,
    seq               BIGINT      GENERATED ALWAYS AS IDENTITY,  -- ordem estável, mesmo com created_at igual
    conversation_id   UUID        NOT NULL REFERENCES assistant_conversations (id) ON DELETE CASCADE,
    role              VARCHAR(10) NOT NULL,
    content           TEXT        NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL,
    -- só em mensagens do usuário: a mesma chave na mesma conversa devolve a resposta já dada
    idempotency_key   VARCHAR(100),
    -- só em respostas do assistente
    reply_to_id       UUID REFERENCES assistant_messages (id),
    prompt_version    VARCHAR(20),
    tool_calls        JSONB,
    pending_action_ids JSONB,
    prompt_tokens     INT,
    completion_tokens INT,
    CONSTRAINT ck_assistant_messages_role CHECK (role IN ('USER', 'ASSISTANT')),
    CONSTRAINT uq_assistant_messages_key UNIQUE (conversation_id, idempotency_key),
    CONSTRAINT uq_assistant_messages_reply UNIQUE (reply_to_id)
);

CREATE INDEX ix_assistant_messages_conversation ON assistant_messages (conversation_id, seq);

-- Valores reais por trás dos tokens de PII de uma conversa. Usados só pelo backend, para trocar o token
-- pelo valor quando uma tool precisa (ex.: documento do passageiro no hold). Nunca vão para o modelo.
CREATE TABLE assistant_pii_tokens (
    conversation_id UUID         NOT NULL REFERENCES assistant_conversations (id) ON DELETE CASCADE,
    token           VARCHAR(20)  NOT NULL,
    value           VARCHAR(200) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (conversation_id, token),
    CONSTRAINT uq_assistant_pii_value UNIQUE (conversation_id, value)
);
