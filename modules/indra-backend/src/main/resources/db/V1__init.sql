-- INDRA AI - schema version 1
-- Portable subset of SQL accepted by SQLite 3.35+ and PostgreSQL 13+.
-- The backend applies these statements automatically on first start
-- (see ai.indra.backend.store.JdbcStore). Running them manually is also fine.

CREATE TABLE IF NOT EXISTS indra_documents (
    collection  VARCHAR(64)  NOT NULL,
    id          VARCHAR(64)  NOT NULL,
    user_id     VARCHAR(64)  NULL,
    lookup_key  VARCHAR(255) NULL,
    updated_at  BIGINT       NOT NULL,
    doc         TEXT         NOT NULL,
    PRIMARY KEY (collection, id)
);

CREATE INDEX IF NOT EXISTS idx_indra_docs_user
    ON indra_documents (collection, user_id, updated_at);

CREATE INDEX IF NOT EXISTS idx_indra_docs_lookup
    ON indra_documents (collection, lookup_key);

-- Collections stored in indra_documents.collection:
--   users, memories, conversations, messages, tasks, settings, devices,
--   pairings, licenses, apikeys, apikey_secrets, audit, config, meta
--
-- API keys are encrypted with AES-256-GCM before they reach this table; the
-- encryption key is derived from INDRA_MASTER_KEY and never stored in the DB.
