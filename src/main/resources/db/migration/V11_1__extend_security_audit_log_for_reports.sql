-- V11_1 — Typed report columns on t_security_audit_log.
--
-- Phase 2 of the "Unified Audit & Reports" feature. Adds the 16 typed columns
-- that back the three JSON report endpoints (Audit / Access / Settings) plus two
-- partial indexes for the settings-area and module report filters (N1).
--
-- All 15 data-bearing columns (every one except the reserved, read-derived
-- `device`) participate in the hash chain via SecurityAuditChainService#serialize.
-- Extending the hash input breaks chains written before this migration, so
-- dev/staging must run one rehash-on-boot pass (igrp.audit.chain.rehash-on-boot)
-- afterwards; production never rehashes (R2.2 / R2.6 / N9).
--
-- Guarded and idempotent, mirroring V10_1: a no-op on a fresh container where
-- Hibernate has not created the table yet (ddl-auto=update then creates the
-- columns from the entity), and the full ALTER on any previously-booted DB.

DO $$
BEGIN
    IF to_regclass('t_security_audit_log') IS NULL THEN
        RAISE NOTICE 't_security_audit_log not present yet; skipping V11_1 (columns created from the entity by Hibernate on a fresh dev DB).';
        RETURN;
    END IF;

    ALTER TABLE t_security_audit_log ADD COLUMN IF NOT EXISTS application_module  VARCHAR(100);
    ALTER TABLE t_security_audit_log ADD COLUMN IF NOT EXISTS access_role         VARCHAR(255);
    ALTER TABLE t_security_audit_log ADD COLUMN IF NOT EXISTS operation_state     VARCHAR(50);
    ALTER TABLE t_security_audit_log ADD COLUMN IF NOT EXISTS device              VARCHAR(255);  -- reserved; derived at read time
    ALTER TABLE t_security_audit_log ADD COLUMN IF NOT EXISTS authorized_by       VARCHAR(255);
    ALTER TABLE t_security_audit_log ADD COLUMN IF NOT EXISTS status              VARCHAR(20);
    ALTER TABLE t_security_audit_log ADD COLUMN IF NOT EXISTS action              VARCHAR(100);
    ALTER TABLE t_security_audit_log ADD COLUMN IF NOT EXISTS settings_area       VARCHAR(20);
    ALTER TABLE t_security_audit_log ADD COLUMN IF NOT EXISTS settings_entity_type VARCHAR(30);
    ALTER TABLE t_security_audit_log ADD COLUMN IF NOT EXISTS settings_operation  VARCHAR(30);
    ALTER TABLE t_security_audit_log ADD COLUMN IF NOT EXISTS entity_name         VARCHAR(500);
    ALTER TABLE t_security_audit_log ADD COLUMN IF NOT EXISTS related_entity      VARCHAR(500);
    ALTER TABLE t_security_audit_log ADD COLUMN IF NOT EXISTS previous_value      TEXT;
    ALTER TABLE t_security_audit_log ADD COLUMN IF NOT EXISTS new_value           TEXT;
    ALTER TABLE t_security_audit_log ADD COLUMN IF NOT EXISTS period_start        TIMESTAMPTZ;
    ALTER TABLE t_security_audit_log ADD COLUMN IF NOT EXISTS period_end          TIMESTAMPTZ;

    CREATE INDEX IF NOT EXISTS idx_audit_settings_area
        ON t_security_audit_log (settings_area, timestamp DESC)
        WHERE settings_area IS NOT NULL;
    CREATE INDEX IF NOT EXISTS idx_audit_module
        ON t_security_audit_log (application_module, timestamp DESC)
        WHERE application_module IS NOT NULL;
END $$;
