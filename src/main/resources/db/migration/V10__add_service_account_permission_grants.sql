-- V10 — direct permission grants on service accounts.
--
-- The V9 model only covered role-derived permissions. M2M scopes are often
-- narrow enough (e.g. just "igrp.m2m.sync") that spinning up a dedicated role
-- per client is overkill — allow administrators to attach permissions
-- directly to a service account, mirroring how a user record can be granted
-- ad-hoc permissions through its role layer.

CREATE TABLE IF NOT EXISTS t_service_account_permission_grant (
    service_account_id UUID    NOT NULL,
    permission_id      INTEGER NOT NULL,
    granted_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (service_account_id, permission_id)
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_name = 't_service_account_permission_grant'
          AND constraint_name = 'fk_service_account_permission_grant_account'
    ) THEN
        ALTER TABLE t_service_account_permission_grant
            ADD CONSTRAINT fk_service_account_permission_grant_account
            FOREIGN KEY (service_account_id) REFERENCES t_service_account(id)
            ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_name = 't_service_account_permission_grant'
          AND constraint_name = 'fk_service_account_permission_grant_permission'
    ) THEN
        ALTER TABLE t_service_account_permission_grant
            ADD CONSTRAINT fk_service_account_permission_grant_permission
            FOREIGN KEY (permission_id) REFERENCES t_permission(id)
            ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_service_account_permission_grant_permission
    ON t_service_account_permission_grant (permission_id);
