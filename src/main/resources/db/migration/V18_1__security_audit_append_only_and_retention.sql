-- V18_1 — t_security_audit_log is append-only, with no bypass, and old rows
-- leave only through archive-then-delete retention (v3-23, #30).
--
-- V10_1 let UPDATE/DELETE through whenever the session set app.audit_purge =
-- 'true'. Any code path with a connection could set it, so the trail was only
-- as immutable as the application's good behaviour. Now:
--   * UPDATE is always refused, by the trigger and by REVOKE.
--   * DELETE is refused unless the row is (a) not the GENESIS anchor,
--     (b) older than 365 days, the hard floor no configuration can lower, and
--     (c) covered by a t_security_audit_archive record, written by retention in
--     the same transaction once the row is safely in object storage.
--
-- t_security_audit_archive records each archived run of sequence numbers with
-- the hash just before it and its last hash, so the chain validator can bridge
-- the gap the deletion leaves. It is append-only too.
--
-- REVOKE applies to AM's own database user, which owns the tables. An owner or
-- a superuser can grant the privileges back, which is why the triggers, which
-- hold for every role, are the primary guard.
--
-- Idempotent: every statement is guarded or re-runnable.

CREATE TABLE IF NOT EXISTS t_security_audit_archive (
    id                UUID PRIMARY KEY,
    first_sequence    BIGINT       NOT NULL,
    last_sequence     BIGINT       NOT NULL,
    previous_hash     VARCHAR(64)  NOT NULL,
    last_hash         VARCHAR(64)  NOT NULL,
    row_count         BIGINT       NOT NULL,
    storage_key       VARCHAR(512) NOT NULL,
    cutoff            TIMESTAMP(6) NOT NULL,
    created_at        TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT ux_security_audit_archive_first_sequence UNIQUE (first_sequence),
    CONSTRAINT ck_security_audit_archive_range CHECK (first_sequence > 0 AND last_sequence >= first_sequence)
);

CREATE OR REPLACE FUNCTION trg_security_audit_append_only() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 't_security_audit_log is append-only; UPDATE is not allowed';
    END IF;

    IF OLD.sequence_number = 0 THEN
        RAISE EXCEPTION 't_security_audit_log: the GENESIS anchor is never deleted';
    END IF;
    IF OLD."timestamp" >= localtimestamp - INTERVAL '365 days' THEN
        RAISE EXCEPTION 't_security_audit_log: row % is inside the 365-day minimum retention', OLD.sequence_number;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM t_security_audit_archive a
                    WHERE OLD.sequence_number BETWEEN a.first_sequence AND a.last_sequence) THEN
        RAISE EXCEPTION 't_security_audit_log: row % is not archived', OLD.sequence_number;
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION trg_security_audit_archive_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 't_security_audit_archive is append-only; % is not allowed', TG_OP;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS no_update_delete_security_audit_archive ON t_security_audit_archive;
CREATE TRIGGER no_update_delete_security_audit_archive
    BEFORE UPDATE OR DELETE ON t_security_audit_archive
    FOR EACH ROW EXECUTE FUNCTION trg_security_audit_archive_append_only();

REVOKE UPDATE, DELETE ON t_security_audit_archive FROM PUBLIC, CURRENT_USER;

DO $$
BEGIN
    IF to_regclass('t_security_audit_log') IS NULL THEN
        RAISE NOTICE 't_security_audit_log not present yet; skipping its V18_1 trigger and grants.';
        RETURN;
    END IF;

    EXECUTE 'DROP TRIGGER IF EXISTS no_update_delete_security_audit ON t_security_audit_log';
    EXECUTE 'CREATE TRIGGER no_update_delete_security_audit '
         || 'BEFORE UPDATE OR DELETE ON t_security_audit_log '
         || 'FOR EACH ROW EXECUTE FUNCTION trg_security_audit_append_only()';

    EXECUTE 'REVOKE UPDATE ON t_security_audit_log FROM PUBLIC, CURRENT_USER';
END $$;
