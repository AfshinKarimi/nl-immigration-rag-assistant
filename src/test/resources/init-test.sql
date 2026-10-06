-- Enable the pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS document_chunks (
    id              UUID PRIMARY KEY,
    content         TEXT NOT NULL,
    metadata        JSONB DEFAULT '{}'::jsonb,
    embedding       vector(768) NOT NULL,
    content_tsv     TSVECTOR GENERATED ALWAYS AS (to_tsvector('english', content)) STORED,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_document_chunks_embedding
    ON document_chunks
    USING hnsw (embedding vector_cosine_ops);

CREATE INDEX IF NOT EXISTS idx_document_chunks_tsv
    ON document_chunks
    USING gin (content_tsv);

CREATE INDEX IF NOT EXISTS idx_document_chunks_metadata
    ON document_chunks
    USING gin (metadata);
