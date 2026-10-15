-- Base de políticas do RAG (módulo assistant). Formato que o PgVectorStore do Spring AI espera; a criação
-- automática dele fica desligada (spring.ai.vectorstore.pgvector.initialize-schema=false) porque o schema
-- é do Flyway. 1536 dimensões = text-embedding-3-small.
-- Só política (bagagem, cancelamento, tarifas). Preço e disponibilidade nunca entram aqui: vêm das tools.
CREATE TABLE vector_store (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content   TEXT,
    metadata  JSON,
    embedding VECTOR(1536)
);

CREATE INDEX ix_vector_store_embedding ON vector_store USING hnsw (embedding vector_cosine_ops);
