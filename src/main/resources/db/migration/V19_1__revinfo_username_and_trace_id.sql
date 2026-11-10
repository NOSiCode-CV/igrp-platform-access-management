-- V19_1 — Envers revision carries who and which request (v3-24, #31).
--
-- RevisionInfoEntity replaces Hibernate's default revision on revinfo. Each
-- revision now records the username of the change and the request's
-- correlation id (the OTel trace id, or the X-Correlation-ID fallback), so the
-- change history joins the audit row and the trace in Tempo.
--
-- Nullable: revisions written before this migration keep NULL in both.
--
-- Guarded and idempotent, like V16_1. On a fresh database revinfo comes from
-- base-schema.sql before Flyway runs, so the columns are added here. The guard
-- only matters where revinfo does not exist at all: there ddl-auto=update
-- creates it from RevisionInfoEntity, while ddl-auto=validate (staging,
-- production) would stop startup, as it does for any missing table.

DO $$
BEGIN
    IF to_regclass('revinfo') IS NULL THEN
        RAISE NOTICE 'revinfo not present yet; skipping V19_1.';
        RETURN;
    END IF;

    ALTER TABLE revinfo ADD COLUMN IF NOT EXISTS username VARCHAR(255);
    ALTER TABLE revinfo ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64);
END $$;
