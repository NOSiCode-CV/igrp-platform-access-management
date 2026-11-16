-- The data of an AM 0.2 installation (v3-29, #37), on the 0.2 schema
-- (pre-flyway-schema.sql + V6..V16_1). Ids from 900 so the identity sequences,
-- still at 1, never collide with rows the 0.3 migrations insert.

INSERT INTO t_application (id, created_by, created_date, code, name, status, type, slug)
VALUES (900, 'system', now(), 'APP_IGRP_CENTER', 'iGRP Application Center', 'ACTIVE', 'INTERNAL', 'app-center'),
       (901, 'system', now(), 'APP_NOTIFICATIONS', 'Notifications', 'ACTIVE', 'INTERNAL', 'notifications');

INSERT INTO t_department (id, created_by, created_date, code, name, status)
VALUES (900, 'system', now(), 'DEPT_IGRP', 'iGRP', 'ACTIVE'),
       (901, 'system', now(), 'DEPT_RH', 'Recursos Humanos', 'ACTIVE');

INSERT INTO t_department_application (department_id, application_id)
VALUES (900, 900), (901, 900), (901, 901);

-- Root's role and the default permission ConfigurationService creates.
INSERT INTO t_role (id, created_by, created_date, code, name, status, department)
VALUES (900, 'system', now(), 'DEPT_IGRP.superadmin', 'Super Admin', 'ACTIVE', 900),
       (901, 'system', now(), 'RH_ADMIN', 'Administrador RH', 'ACTIVE', 901),
       (902, 'system', now(), 'RH_NOTIFY', 'Notificações RH', 'ACTIVE', 901);

INSERT INTO t_permission (id, created_by, created_date, name, description, status)
VALUES (900, 'system', now(), 'DEPT_IGRP.manage_access', 'iGRP Manage Access Permission', 'ACTIVE'),
       -- AM's own permissions (@IgrpPermission, synced at startup in 0.2)
       (901, 'system', now(), 'igrp.users.view', 'View users', 'ACTIVE'),
       (902, 'system', now(), 'igrp.users.list', 'List users', 'ACTIVE'),
       (903, 'system', now(), 'igrp.departments.view', 'View departments', 'ACTIVE'),
       (904, 'system', now(), 'igrp.m2m.sync', 'M2M sync', 'ACTIVE'),
       (905, 'system', now(), 'igrp.users.pii.view', 'View PII', 'ACTIVE'),
       -- Another service's legacy permission: a business permission
       (906, 'system', now(), 'igrp.notification.send', 'Send notifications', 'ACTIVE'),
       (907, 'system', now(), 'igrp.applications.view', 'View applications', 'ACTIVE'),
       (908, 'system', now(), 'igrp.audit.view', 'View audit', 'ACTIVE');

INSERT INTO t_role_permission (role_id, permission)
VALUES (900, 900), (900, 901),
       (901, 901), (901, 902), (901, 903), (901, 905), (901, 907), (901, 908),
       (902, 906);

INSERT INTO t_permission_department (permission_id, department)
VALUES (900, 900), (901, 901), (902, 901), (903, 901), (906, 901), (908, 901);

INSERT INTO t_application_role (application_id, roles) VALUES (900, 900), (900, 901), (901, 902);

INSERT INTO t_user (id, created_by, created_date, email, username, name, status)
VALUES ('00000000-0000-0000-0000-000000000900', 'system', now(), 'root@igrp.cv', 'root', 'Root', 'ACTIVE'),
       ('00000000-0000-0000-0000-000000000901', 'system', now(), 'ana@rh.cv', 'ana', 'Ana RH', 'ACTIVE');

INSERT INTO t_user_role_assignment (user_id, role_id, assigned_at)
VALUES ('00000000-0000-0000-0000-000000000900', 900, now()),
       ('00000000-0000-0000-0000-000000000901', 901, now()),
       ('00000000-0000-0000-0000-000000000901', 902, now());

INSERT INTO t_oauth_client (id, client_id, client_name, client_secret, active, access_token_ttl,
                            refresh_token_ttl, authorization_code_ttl, require_pkce, application_id, created_at)
VALUES ('00000000-0000-0000-0000-00000000c900', 'notifications-api', 'Notifications', '{bcrypt}x', true, 300,
        3600, 60, true, 901, now());

INSERT INTO t_service_account (id, name, active, oauth_client_id, application_id, created_at)
VALUES ('00000000-0000-0000-0000-00000000a900', 'notifications-api SA', true,
        '00000000-0000-0000-0000-00000000c900', 901, now());

INSERT INTO t_service_account_permission_grant (service_account_id, permission_id, granted_at)
VALUES ('00000000-0000-0000-0000-00000000a900', 904, now()),
       ('00000000-0000-0000-0000-00000000a900', 902, now());
