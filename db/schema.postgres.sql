-- INDRA AI - PostgreSQL production schema (optional; the backend creates it automatically)
CREATE TABLE IF NOT EXISTS indra_documents (
    collection  VARCHAR(64)  NOT NULL,
    id          VARCHAR(64)  NOT NULL,
    user_id     VARCHAR(64),
    lookup_key  VARCHAR(255),
    updated_at  BIGINT       NOT NULL,
    doc         TEXT         NOT NULL,
    PRIMARY KEY (collection, id)
);
CREATE INDEX IF NOT EXISTS idx_indra_docs_user ON indra_documents (collection, user_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_indra_docs_lookup ON indra_documents (collection, lookup_key);

-- Recommended backup (run nightly):
--   pg_dump --format=custom indra_ai > indra_ai_$(date +%F).dump
