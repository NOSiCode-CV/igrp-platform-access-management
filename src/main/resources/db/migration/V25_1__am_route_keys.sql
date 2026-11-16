-- V25_1 — AM's own permissions on the route key convention (v3-28, #36).
--
-- AM's routes are protected by the 0.3 starter's route filter, like every
-- service's: each needs its key, access-management-api.<resource>.<action>.
-- AM's legacy igrp.* permissions, which its @PreAuthorize checked, are
-- converted through this fixed mapping, derived from AM's controllers (each
-- route's old permission -> that route's key) and reviewed in the merge request:
--
--   * every role, department and service account holding an old permission
--     gets the keys it maps to, with the same scope;
--   * the keys of /api/m2m/** and /api/routes/register (machine_only) go to
--     service accounts only: the 0.2 M2M filter refused every other caller there;
--   * the old assignments are removed and the old permission is DELETED:
--     nothing checks it any more.
--
-- Only AM's own permissions are touched: the 36 names below. Other services'
-- igrp.* permissions (igrp.notification.*, ...) are business permissions, left
-- for the migration review (#37).
--
-- The keys are created here, in AM's application (APP_IGRP_CENTER), only when
-- an old permission is held; AM's route registration at startup then keeps
-- them and creates the rest. On a fresh database (no application yet) nothing
-- is converted.
--
-- AM's igrp.* permissions that guarded no route (igrp.users.pii.*,
-- igrp.session.view, igrp.audit.list, ...) map to nothing (new_key NULL): they
-- are DELETED with their assignments.
--
-- Idempotent: a second run finds none of these permissions still ACTIVE. No
-- statement relies on a join-table primary key (a 0.2 database created by
-- Hibernate may lack one): existing rows are skipped with NOT EXISTS.

CREATE TEMPORARY TABLE am_route_key_mapping (
    old_name     VARCHAR(255) NOT NULL,
    new_key      VARCHAR(255),
    machine_only BOOLEAN      NOT NULL
) ON COMMIT DROP;

INSERT INTO am_route_key_mapping (old_name, new_key, machine_only) VALUES
        ('igrp.applications.create', 'access-management-api.applications.create', FALSE),
        ('igrp.applications.customize', 'access-management-api.global-configuration.create', FALSE),
        ('igrp.applications.delete', 'access-management-api.applications.delete', FALSE),
        ('igrp.applications.delete', 'access-management-api.applications.menus.delete', FALSE),
        ('igrp.applications.list', 'access-management-api.applications.list', FALSE),
        ('igrp.applications.manage', 'access-management-api.applications.menus.create', FALSE),
        ('igrp.applications.manage', 'access-management-api.applications.menus.roles.create', FALSE),
        ('igrp.applications.manage', 'access-management-api.applications.menus.roles.delete-all', FALSE),
        ('igrp.applications.manage', 'access-management-api.applications.menus.update', FALSE),
        ('igrp.applications.manage', 'access-management-api.applications.resources.create', FALSE),
        ('igrp.applications.manage', 'access-management-api.applications.resources.delete-all', FALSE),
        ('igrp.applications.update', 'access-management-api.applications.custom-fields.create', FALSE),
        ('igrp.applications.update', 'access-management-api.applications.custom-fields.delete-all', FALSE),
        ('igrp.applications.update', 'access-management-api.applications.update', FALSE),
        ('igrp.applications.update', 'access-management-api.files.private.create', FALSE),
        ('igrp.applications.update', 'access-management-api.files.public.create', FALSE),
        ('igrp.applications.view', 'access-management-api.applications.by-code.read', FALSE),
        ('igrp.applications.view', 'access-management-api.applications.custom-fields.list', FALSE),
        ('igrp.applications.view', 'access-management-api.applications.menus.list', FALSE),
        ('igrp.applications.view', 'access-management-api.applications.read', FALSE),
        ('igrp.applications.view', 'access-management-api.files.url.list', FALSE),
        ('igrp.audit.view', 'access-management-api.auth.audit.list', FALSE),
        ('igrp.audit.view', 'access-management-api.auth.audit.validate.create', FALSE),
        ('igrp.audit.view', 'access-management-api.auth.reports.access-csv.list', FALSE),
        ('igrp.audit.view', 'access-management-api.auth.reports.access-pdf.list', FALSE),
        ('igrp.audit.view', 'access-management-api.auth.reports.access-xlsx.list', FALSE),
        ('igrp.audit.view', 'access-management-api.auth.reports.access.archive.create', FALSE),
        ('igrp.audit.view', 'access-management-api.auth.reports.access.list', FALSE),
        ('igrp.audit.view', 'access-management-api.auth.reports.archives.list', FALSE),
        ('igrp.audit.view', 'access-management-api.auth.reports.audit-csv.list', FALSE),
        ('igrp.audit.view', 'access-management-api.auth.reports.audit-pdf.list', FALSE),
        ('igrp.audit.view', 'access-management-api.auth.reports.audit-xlsx.list', FALSE),
        ('igrp.audit.view', 'access-management-api.auth.reports.audit.archive.create', FALSE),
        ('igrp.audit.view', 'access-management-api.auth.reports.audit.list', FALSE),
        ('igrp.audit.view', 'access-management-api.auth.reports.settings-csv.list', FALSE),
        ('igrp.audit.view', 'access-management-api.auth.reports.settings-pdf.list', FALSE),
        ('igrp.audit.view', 'access-management-api.auth.reports.settings-xlsx.list', FALSE),
        ('igrp.audit.view', 'access-management-api.auth.reports.settings.archive.create', FALSE),
        ('igrp.audit.view', 'access-management-api.auth.reports.settings.list', FALSE),
        ('igrp.client.create', 'access-management-api.clients.create', FALSE),
        ('igrp.client.create', 'access-management-api.service-accounts.create', FALSE),
        ('igrp.client.delete', 'access-management-api.clients.delete', FALSE),
        ('igrp.client.delete', 'access-management-api.service-accounts.delete', FALSE),
        ('igrp.client.list', 'access-management-api.clients.list', FALSE),
        ('igrp.client.list', 'access-management-api.service-accounts.list', FALSE),
        ('igrp.client.update', 'access-management-api.clients.rotate-secret.create', FALSE),
        ('igrp.client.update', 'access-management-api.clients.update', FALSE),
        ('igrp.client.update', 'access-management-api.service-accounts.update', FALSE),
        ('igrp.client.view', 'access-management-api.clients.read', FALSE),
        ('igrp.client.view', 'access-management-api.service-accounts.read', FALSE),
        ('igrp.departments.create', 'access-management-api.departments.create', FALSE),
        ('igrp.departments.delete', 'access-management-api.departments.delete', FALSE),
        ('igrp.departments.list', 'access-management-api.departments.list', FALSE),
        ('igrp.departments.list', 'access-management-api.m2m.departments.create', TRUE),
        ('igrp.departments.manage', 'access-management-api.departments.applications.available.list', FALSE),
        ('igrp.departments.manage', 'access-management-api.departments.applications.create', FALSE),
        ('igrp.departments.manage', 'access-management-api.departments.applications.delete-all', FALSE),
        ('igrp.departments.manage', 'access-management-api.departments.applications.menus.available.list', FALSE),
        ('igrp.departments.manage', 'access-management-api.departments.applications.menus.create', FALSE),
        ('igrp.departments.manage', 'access-management-api.departments.applications.menus.delete-all', FALSE),
        ('igrp.departments.manage', 'access-management-api.departments.permissions.available.list', FALSE),
        ('igrp.departments.manage', 'access-management-api.departments.permissions.create', FALSE),
        ('igrp.departments.manage', 'access-management-api.departments.permissions.delete-all', FALSE),
        ('igrp.departments.manage', 'access-management-api.departments.resources.available.list', FALSE),
        ('igrp.departments.manage', 'access-management-api.departments.resources.create', FALSE),
        ('igrp.departments.manage', 'access-management-api.departments.resources.delete-all', FALSE),
        ('igrp.departments.manage', 'access-management-api.departments.roles.create', FALSE),
        ('igrp.departments.manage', 'access-management-api.departments.roles.delete', FALSE),
        ('igrp.departments.manage', 'access-management-api.departments.roles.permissions.available.list', FALSE),
        ('igrp.departments.manage', 'access-management-api.departments.roles.permissions.create', FALSE),
        ('igrp.departments.manage', 'access-management-api.departments.roles.permissions.delete-all', FALSE),
        ('igrp.departments.manage', 'access-management-api.departments.roles.permissions.scopes.update-all', FALSE),
        ('igrp.departments.manage', 'access-management-api.departments.roles.update', FALSE),
        ('igrp.departments.update', 'access-management-api.departments.update', FALSE),
        ('igrp.departments.view', 'access-management-api.departments.applications.list', FALSE),
        ('igrp.departments.view', 'access-management-api.departments.applications.menus.list', FALSE),
        ('igrp.departments.view', 'access-management-api.departments.by-code.read', FALSE),
        ('igrp.departments.view', 'access-management-api.departments.permissions.list', FALSE),
        ('igrp.departments.view', 'access-management-api.departments.read', FALSE),
        ('igrp.departments.view', 'access-management-api.departments.resources.list', FALSE),
        ('igrp.departments.view', 'access-management-api.departments.roles.children.list', FALSE),
        ('igrp.departments.view', 'access-management-api.departments.roles.list', FALSE),
        ('igrp.departments.view', 'access-management-api.departments.roles.parents.list', FALSE),
        ('igrp.departments.view', 'access-management-api.departments.roles.permissions.list', FALSE),
        ('igrp.departments.view', 'access-management-api.m2m.roles.create', TRUE),
        ('igrp.departments.view', 'access-management-api.roles.by-code.read', FALSE),
        ('igrp.departments.view', 'access-management-api.roles.read', FALSE),
        ('igrp.m2m.sync', 'access-management-api.m2m.global-configuration.list', TRUE),
        ('igrp.m2m.sync', 'access-management-api.m2m.sync.applications.create', TRUE),
        ('igrp.m2m.sync', 'access-management-api.m2m.sync.applications.menus.create', TRUE),
        ('igrp.m2m.sync', 'access-management-api.m2m.sync.permissions.create', TRUE),
        ('igrp.m2m.sync', 'access-management-api.m2m.sync.resources.create', TRUE),
        ('igrp.m2m.sync', 'access-management-api.routes.register.create', TRUE),
        ('igrp.session.admin', 'access-management-api.admin.sessions.departments.kill.create', FALSE),
        ('igrp.session.admin', 'access-management-api.admin.sessions.kill.create', FALSE),
        ('igrp.session.admin', 'access-management-api.admin.sessions.list', FALSE),
        ('igrp.session.admin', 'access-management-api.admin.sessions.roles.kill.create', FALSE),
        ('igrp.session.admin', 'access-management-api.admin.sessions.roles.read', FALSE),
        ('igrp.session.admin', 'access-management-api.admin.sessions.statistics.list', FALSE),
        ('igrp.session.admin', 'access-management-api.admin.sessions.users.read', FALSE),
        ('igrp.session.admin', 'access-management-api.admin.sessions.users.refresh.create', FALSE),
        ('igrp.session.admin', 'access-management-api.admin.users.force-reauth.create', FALSE),
        ('igrp.session.admin', 'access-management-api.admin.users.logout-all.create', FALSE),
        ('igrp.session.admin', 'access-management-api.admin.users.sessions.kill.create', FALSE),
        ('igrp.users.create', 'access-management-api.users.invite.create', FALSE),
        ('igrp.users.list', 'access-management-api.m2m.users.create', TRUE),
        ('igrp.users.list', 'access-management-api.users.list', FALSE),
        ('igrp.users.manage', 'access-management-api.users.departments.roles.create', FALSE),
        ('igrp.users.manage', 'access-management-api.users.departments.roles.delete-all', FALSE),
        ('igrp.users.manage', 'access-management-api.users.invite.delete', FALSE),
        ('igrp.users.manage', 'access-management-api.users.invite.list', FALSE),
        ('igrp.users.manage', 'access-management-api.users.invite.update', FALSE),
        ('igrp.users.manage', 'access-management-api.users.roles.active.create', FALSE),
        ('igrp.users.manage', 'access-management-api.users.roles.active.list', FALSE),
        ('igrp.users.manage', 'access-management-api.users.status.update-all', FALSE),
        ('igrp.users.update', 'access-management-api.files.private.create', FALSE),
        ('igrp.users.update', 'access-management-api.files.public.create', FALSE),
        ('igrp.users.update', 'access-management-api.users.metadata.update-all', FALSE),
        ('igrp.users.update', 'access-management-api.users.update', FALSE),
        ('igrp.users.view', 'access-management-api.applications.by-user.read', FALSE),
        ('igrp.users.view', 'access-management-api.files.url.list', FALSE),
        ('igrp.users.view', 'access-management-api.users.applications.list', FALSE),
        ('igrp.users.view', 'access-management-api.users.applications.menus.list', FALSE),
        ('igrp.users.view', 'access-management-api.users.departments.list', FALSE),
        ('igrp.users.view', 'access-management-api.users.departments.roles.list', FALSE),
        ('igrp.users.view', 'access-management-api.users.invite.read', FALSE),
        ('igrp.users.view', 'access-management-api.users.metadata.list', FALSE),
        ('igrp.users.view', 'access-management-api.users.permissions.list', FALSE),
        ('igrp.users.view', 'access-management-api.users.read', FALSE),
        ('igrp.users.view', 'access-management-api.users.roles.list', FALSE),
        ('igrp.audit.list', NULL, FALSE),
        ('igrp.audit.view_by_user', NULL, FALSE),
        ('igrp.client.manage', NULL, FALSE),
        ('igrp.session.create', NULL, FALSE),
        ('igrp.session.kill', NULL, FALSE),
        ('igrp.session.manage', NULL, FALSE),
        ('igrp.session.view', NULL, FALSE),
        ('igrp.users.delete', NULL, FALSE),
        ('igrp.users.pii.update', NULL, FALSE),
        ('igrp.users.pii.view', NULL, FALSE);

DO $$
DECLARE
    app_id INTEGER;
BEGIN
    SELECT id INTO app_id FROM t_application WHERE code = 'APP_IGRP_CENTER' AND status <> 'DELETED';
    IF app_id IS NULL THEN
        RAISE NOTICE 'APP_IGRP_CENTER not present (fresh database): nothing to convert in V25_1.';
        RETURN;
    END IF;

    -- AM's old permissions still to convert, and the keys they map to.
    CREATE TEMPORARY TABLE am_legacy ON COMMIT DROP AS
        SELECT p.id AS old_id, m.new_key, m.machine_only
          FROM t_permission p
          JOIN am_route_key_mapping m ON m.old_name = p.name
         WHERE p.permission_key IS NULL AND p.status <> 'DELETED';

    INSERT INTO t_permission (permission_key, name, status, service, application_id,
                              created_by, created_date, last_modified_by, last_modified_date)
    SELECT DISTINCT l.new_key, l.new_key, 'ACTIVE', 'access-management-api', app_id,
                    'system', now(), 'system', now()
      FROM am_legacy l
     WHERE l.new_key IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM t_permission p WHERE p.permission_key = l.new_key);

    -- A role holds one scope per key. When two of its old permissions map to the
    -- same key with different scopes: all, else department (the default), else
    -- the first of the others.
    INSERT INTO t_role_permission (role_id, permission, scope)
    SELECT rp.role_id, k.id,
           CASE WHEN bool_or(rp.scope = 'all') THEN 'all'
                WHEN bool_or(rp.scope = 'department') THEN 'department'
                ELSE min(rp.scope) END
      FROM am_legacy l
      JOIN t_role_permission rp ON rp.permission = l.old_id
      JOIN t_permission k ON k.permission_key = l.new_key
     WHERE NOT l.machine_only
       AND NOT EXISTS (SELECT 1 FROM t_role_permission e WHERE e.role_id = rp.role_id AND e.permission = k.id)
     GROUP BY rp.role_id, k.id;

    INSERT INTO t_permission_department (permission_id, department)
    SELECT DISTINCT k.id, pd.department
      FROM am_legacy l
      JOIN t_permission_department pd ON pd.permission_id = l.old_id
      JOIN t_permission k ON k.permission_key = l.new_key
     WHERE NOT l.machine_only
       AND NOT EXISTS (SELECT 1 FROM t_permission_department e
                        WHERE e.permission_id = k.id AND e.department = pd.department);

    INSERT INTO t_service_account_permission_grant (permission_id, service_account_id, granted_at)
    SELECT k.id, g.service_account_id, min(g.granted_at)
      FROM am_legacy l
      JOIN t_service_account_permission_grant g ON g.permission_id = l.old_id
      JOIN t_permission k ON k.permission_key = l.new_key
     WHERE NOT EXISTS (SELECT 1 FROM t_service_account_permission_grant e
                        WHERE e.permission_id = k.id AND e.service_account_id = g.service_account_id)
     GROUP BY k.id, g.service_account_id;

    -- The old permissions protect nothing now.
    DELETE FROM t_role_permission WHERE permission IN (SELECT old_id FROM am_legacy);
    DELETE FROM t_permission_department WHERE permission_id IN (SELECT old_id FROM am_legacy);
    DELETE FROM t_service_account_permission_grant WHERE permission_id IN (SELECT old_id FROM am_legacy);
    DELETE FROM t_resource_permission WHERE permission IN (SELECT old_id FROM am_legacy);
    DELETE FROM t_resource_item_permissions WHERE permissions_id IN (SELECT old_id FROM am_legacy);
    UPDATE t_permission SET status = 'DELETED', last_modified_by = 'system', last_modified_date = now()
     WHERE id IN (SELECT old_id FROM am_legacy);
END $$;
