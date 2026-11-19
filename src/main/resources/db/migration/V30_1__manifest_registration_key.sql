-- V30_1 — Page manifest registration (v3-27, #35).
--
-- Frontends register their page manifests at startup with POST
-- /api/manifests/register, whose key is
-- access-management-api.manifests.register.create. The service accounts that
-- already register routes (they hold access-management-api.routes.register.create,
-- which the clients of igrp.m2m.sync received in V25_1) get it too, so a
-- frontend's existing client can register its pages without a manual grant.
--
-- The key is created here, in AM's application (APP_IGRP_CENTER), only when a
-- service account is to hold it; AM's own route registration at startup then
-- keeps it. On a fresh database (no application yet) nothing happens.
--
-- Idempotent: existing rows are skipped with NOT EXISTS.

DO $$
DECLARE
    app_id INTEGER;
BEGIN
    SELECT id INTO app_id FROM t_application WHERE code = 'APP_IGRP_CENTER' AND status <> 'DELETED';
    IF app_id IS NULL THEN
        RAISE NOTICE 'APP_IGRP_CENTER not present (fresh database): nothing to grant in V30_1.';
        RETURN;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM t_service_account_permission_grant g
          JOIN t_permission p ON p.id = g.permission_id
         WHERE p.permission_key = 'access-management-api.routes.register.create' AND p.status <> 'DELETED') THEN
        RETURN;
    END IF;

    INSERT INTO t_permission (permission_key, name, status, service, application_id,
                              created_by, created_date, last_modified_by, last_modified_date)
    SELECT 'access-management-api.manifests.register.create', 'access-management-api.manifests.register.create',
           'ACTIVE', 'access-management-api', app_id, 'system', now(), 'system', now()
     WHERE NOT EXISTS (SELECT 1 FROM t_permission
                        WHERE permission_key = 'access-management-api.manifests.register.create');

    INSERT INTO t_service_account_permission_grant (permission_id, service_account_id, granted_at)
    SELECT m.id, g.service_account_id, now()
      FROM t_service_account_permission_grant g
      JOIN t_permission r ON r.id = g.permission_id
           AND r.permission_key = 'access-management-api.routes.register.create'
      JOIN t_permission m ON m.permission_key = 'access-management-api.manifests.register.create'
     WHERE NOT EXISTS (SELECT 1 FROM t_service_account_permission_grant e
                        WHERE e.permission_id = m.id AND e.service_account_id = g.service_account_id);
END $$;
