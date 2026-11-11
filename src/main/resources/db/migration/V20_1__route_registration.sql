-- V20_1 — Route registration (v3-02, #10), AM side.
--
-- Every route of a service becomes a permission identified by an immutable key
-- ({service}.{resource path}.{action}). A permission now records:
--   permission_key     the key; unique; NULL for the legacy igrp.* permissions;
--   service            the owning service;
--   application_id     the application of the client that registered it;
--   last_seen_version  the newest service version that reported it.
-- name and description stay as they are: code sets them only when a key is
-- first created, and administrators edit them afterwards.
--
-- t_registered_service binds each service name to the client that registered
-- it first, so another client cannot register under that name.
--
-- Guarded and idempotent: every statement can run again on a migrated database
-- without changing it. last_seen_version is not audited (it changes on every
-- deploy), so t_permission_aud gets only the three audited columns.

DO $$
BEGIN
    IF to_regclass('t_permission') IS NULL THEN
        RAISE NOTICE 't_permission not present yet; skipping the t_permission part of V20_1.';
        RETURN;
    END IF;

    ALTER TABLE t_permission ADD COLUMN IF NOT EXISTS permission_key VARCHAR(255);
    ALTER TABLE t_permission ADD COLUMN IF NOT EXISTS service VARCHAR(120);
    ALTER TABLE t_permission ADD COLUMN IF NOT EXISTS application_id INTEGER;
    ALTER TABLE t_permission ADD COLUMN IF NOT EXISTS last_seen_version VARCHAR(64);

    CREATE UNIQUE INDEX IF NOT EXISTS ux_t_permission_permission_key
        ON t_permission (permission_key) WHERE permission_key IS NOT NULL;
    CREATE INDEX IF NOT EXISTS ix_t_permission_service ON t_permission (service);

    IF to_regclass('t_application') IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_t_permission_application') THEN
        ALTER TABLE t_permission ADD CONSTRAINT fk_t_permission_application
            FOREIGN KEY (application_id) REFERENCES t_application (id);
    END IF;

    IF to_regclass('t_permission_aud') IS NOT NULL THEN
        ALTER TABLE t_permission_aud ADD COLUMN IF NOT EXISTS permission_key VARCHAR(255);
        ALTER TABLE t_permission_aud ADD COLUMN IF NOT EXISTS service VARCHAR(120);
        ALTER TABLE t_permission_aud ADD COLUMN IF NOT EXISTS application_id INTEGER;
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS t_registered_service (
    service            VARCHAR(120)                PRIMARY KEY,
    owner_client_id    VARCHAR(120)                NOT NULL,
    last_version       VARCHAR(64)                 NOT NULL,
    created_at         TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now(),
    last_registered_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now()
);

-- One service per client: a known client under a new service name is a rename (#11).
CREATE UNIQUE INDEX IF NOT EXISTS ux_t_registered_service_owner_client_id
    ON t_registered_service (owner_client_id);
