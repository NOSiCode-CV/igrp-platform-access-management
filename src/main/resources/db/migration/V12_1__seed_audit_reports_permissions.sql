-- V12_1 — Seed the igrp.audit.view permission and grant it to the IGRP admin role.
--
-- Phase 2 of the "Unified Audit & Reports" feature. A single permission,
-- igrp.audit.view, gates the raw audit list, the three report endpoints and
-- (Phase 4) the exports (R4.1 / R4.3). The @IgrpPermission-annotated constant in
-- AuditPermissions also drives framework code-gen; this migration guarantees the
-- row and grant exist on any previously-booted DB regardless of that path.
--
-- Idempotent: re-runs cleanly (NOT EXISTS guards on both the permission and the
-- grant). Guarded on table presence so it is a safe no-op on a fresh container
-- where Hibernate has not created t_permission yet (ConfigurationService/framework
-- seed permissions post-DDL there).
--
-- The grant targets the DEPT_IGRP.Administrator role. If that role is not present
-- (e.g. only the bypassing DEPT_IGRP.superadmin exists), the grant is a no-op —
-- superadmin skips permission checks anyway.

DO $$
DECLARE
    v_permission_id BIGINT;
    v_role_id       BIGINT;
BEGIN
    IF to_regclass('t_permission') IS NULL OR to_regclass('t_role') IS NULL
       OR to_regclass('t_role_permission') IS NULL THEN
        RAISE NOTICE 'permission/role tables not present yet; skipping V12_1 (seeded post-DDL on a fresh DB).';
        RETURN;
    END IF;

    -- 1. Ensure the permission row exists.
    IF NOT EXISTS (SELECT 1 FROM t_permission WHERE name = 'igrp.audit.view') THEN
        INSERT INTO t_permission
            (name, description, status, created_by, created_date, last_modified_by, last_modified_date)
        VALUES
            ('igrp.audit.view', 'Permission to view audit logs and reports', 'ACTIVE',
             'system', now(), 'system', now());
    END IF;

    SELECT id INTO v_permission_id FROM t_permission WHERE name = 'igrp.audit.view' LIMIT 1;

    -- 2. Grant to DEPT_IGRP.Administrator, if that role exists.
    SELECT id INTO v_role_id FROM t_role WHERE code = 'DEPT_IGRP.Administrator' LIMIT 1;

    IF v_role_id IS NOT NULL AND v_permission_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM t_role_permission
                        WHERE role_id = v_role_id AND permission = v_permission_id) THEN
        INSERT INTO t_role_permission (role_id, permission) VALUES (v_role_id, v_permission_id);
    END IF;
END $$;
