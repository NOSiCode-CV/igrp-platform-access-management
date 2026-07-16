-- V10_1 — Tamper-evident hash chain on t_security_audit_log (+ UUID primary key).
--
-- Phase 1 of the "Unified Audit & Reports" feature. Collapses the duplicate
-- AuthAuditLog trail onto the single SecurityAuditLog table and adds a
-- cryptographic hash chain (previous_hash / current_hash / sequence_number)
-- so any post-hoc edit to an audit row is detectable.
--
-- Design notes:
--   * PK is converted Long -> UUID. Existing IDs are lost, which is acceptable:
--     audit rows are referenced by no FK, and sequence_number becomes the
--     stable ordering key. This is the only chance to align on UUID before the
--     chain locks the row shape.
--   * sequence_number is a plain BIGINT assigned by SecurityAuditChainService
--     inside the pg_advisory_xact_lock critical section (NOT a DB IDENTITY).
--     Assigning it in the same locked section as previous/current hash keeps the
--     sequence CONTIGUOUS (a rolled-back insert leaves no gap) and lets it
--     participate in the hash input — both required by the Phase 1 validation.
--   * The GENESIS anchor (sequence 0) carries current_hash = 'GENESIS'. The
--     chain secret is mixed into every subsequent row's HMAC, so the anchor
--     itself does not need to encode the secret (and we must not embed secrets
--     in a checked-in migration).
--   * Append-only enforcement is a BEFORE UPDATE/DELETE trigger that only lets
--     mutations through when the session GUC app.audit_purge = 'true' (set by
--     the purge command and by rehash-on-boot).
--
-- Idempotent and guarded so it is a safe no-op on a fresh container where
-- Hibernate has not yet created the table (DatabaseMigrationRunner then seeds
-- the chain post-DDL); it does the full conversion on any previously-booted DB.

DO $$
BEGIN
    IF to_regclass('t_security_audit_log') IS NULL THEN
        RAISE NOTICE 't_security_audit_log not present yet; skipping V10_1 (chain seeded post-DDL by DatabaseMigrationRunner).';
        RETURN;
    END IF;

    -- 1. Convert PK Long -> UUID (only if not already uuid).
    IF (SELECT data_type FROM information_schema.columns
          WHERE table_name = 't_security_audit_log' AND column_name = 'id') <> 'uuid' THEN
        ALTER TABLE t_security_audit_log ADD COLUMN id_uuid UUID NOT NULL DEFAULT gen_random_uuid();
        ALTER TABLE t_security_audit_log DROP CONSTRAINT IF EXISTS t_security_audit_log_pkey;
        ALTER TABLE t_security_audit_log DROP COLUMN id;
        ALTER TABLE t_security_audit_log RENAME COLUMN id_uuid TO id;
        ALTER TABLE t_security_audit_log ALTER COLUMN id DROP DEFAULT;
        ALTER TABLE t_security_audit_log ADD PRIMARY KEY (id);
    END IF;

    -- 2. Hash-chain columns.
    ALTER TABLE t_security_audit_log ADD COLUMN IF NOT EXISTS sequence_number BIGINT;
    ALTER TABLE t_security_audit_log ADD COLUMN IF NOT EXISTS previous_hash   VARCHAR(64) NOT NULL DEFAULT '';
    ALTER TABLE t_security_audit_log ADD COLUMN IF NOT EXISTS current_hash    VARCHAR(64) NOT NULL DEFAULT '';
    ALTER TABLE t_security_audit_log ADD COLUMN IF NOT EXISTS epoch_ms        BIGINT;
    ALTER TABLE t_security_audit_log ADD COLUMN IF NOT EXISTS ip_hash         VARCHAR(64);

    -- 3. Backfill sequence_number for pre-existing rows. Their hashes stay empty
    --    until a rehash-on-boot pass (dev/staging) rewrites them; the chain is
    --    considered to start fresh from the GENESIS anchor.
    UPDATE t_security_audit_log s
       SET sequence_number = t.rn
      FROM (SELECT id, ROW_NUMBER() OVER (ORDER BY timestamp NULLS FIRST, ctid) AS rn
              FROM t_security_audit_log
             WHERE sequence_number IS NULL) t
     WHERE s.id = t.id
       AND s.sequence_number IS NULL;

    -- 4. GENESIS anchor at sequence 0.
    IF NOT EXISTS (SELECT 1 FROM t_security_audit_log WHERE sequence_number = 0) THEN
        INSERT INTO t_security_audit_log
            (id, event_type, category, timestamp, sequence_number, previous_hash, current_hash, epoch_ms)
        VALUES
            (gen_random_uuid(), 'SYSTEM_CONFIGURATION_CHANGED', 'SYSTEM', now(), 0, '', 'GENESIS', 0);
    END IF;

    ALTER TABLE t_security_audit_log ALTER COLUMN sequence_number SET NOT NULL;
    CREATE UNIQUE INDEX IF NOT EXISTS ux_security_audit_sequence
        ON t_security_audit_log (sequence_number);
END $$;

-- 5. Append-only trigger. Blocks UPDATE/DELETE unless app.audit_purge = 'true'
--    (set transactionally by the purge command and by rehash-on-boot). INSERTs
--    are always allowed.
CREATE OR REPLACE FUNCTION trg_security_audit_append_only() RETURNS trigger AS $$
BEGIN
    IF current_setting('app.audit_purge', true) IS DISTINCT FROM 'true' THEN
        RAISE EXCEPTION 't_security_audit_log is append-only; % is blocked outside a privileged purge/rehash (set app.audit_purge = ''true'')', TG_OP;
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DO $$
BEGIN
    IF to_regclass('t_security_audit_log') IS NOT NULL THEN
        EXECUTE 'DROP TRIGGER IF EXISTS no_update_delete_security_audit ON t_security_audit_log';
        EXECUTE 'CREATE TRIGGER no_update_delete_security_audit '
             || 'BEFORE UPDATE OR DELETE ON t_security_audit_log '
             || 'FOR EACH ROW EXECUTE FUNCTION trg_security_audit_append_only()';
    END IF;
END $$;
