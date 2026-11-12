-- V23_1 — Scope on role permissions (v3-13, #21).
--
-- Each permission assigned to a role carries a row-visibility scope: all, own,
-- by_role or department. Existing assignments keep today's visibility: all for
-- root (DEPT_IGRP.superadmin), department for every other role. New rows are
-- written by Hibernate's many-to-many mapping without a scope, so the column
-- defaults to department.
--
-- Idempotent: the column and the check are added only when missing, and only
-- rows without a scope are converted.

ALTER TABLE t_role_permission ADD COLUMN IF NOT EXISTS scope VARCHAR(20);

UPDATE t_role_permission rp
   SET scope = CASE WHEN r.code = 'DEPT_IGRP.superadmin' THEN 'all' ELSE 'department' END
  FROM t_role r
 WHERE r.id = rp.role_id
   AND rp.scope IS NULL;

-- Defensive: every row has a role (FK, NOT NULL), so this normally changes nothing.
UPDATE t_role_permission SET scope = 'department' WHERE scope IS NULL;

ALTER TABLE t_role_permission ALTER COLUMN scope SET DEFAULT 'department';
ALTER TABLE t_role_permission ALTER COLUMN scope SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_t_role_permission_scope'
                                              AND conrelid = 't_role_permission'::regclass) THEN
        ALTER TABLE t_role_permission
            ADD CONSTRAINT ck_t_role_permission_scope CHECK (scope IN ('all', 'own', 'by_role', 'department'));
    END IF;
END $$;
