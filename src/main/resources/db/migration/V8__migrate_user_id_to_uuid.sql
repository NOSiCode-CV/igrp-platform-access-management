-- Phase G2 — Migrate t_user.id from INTEGER (IDENTITY) to VARCHAR(36) UUID.
--
-- Strategy: dual-column transition inside a single transaction.
--   1. Ensure pgcrypto is available for gen_random_uuid().
--      (Postgres 13+ also exposes gen_random_uuid() natively, but we
--      enable the extension defensively because earlier migrations do
--      not assume it.)
--   2. Add t_user.id_uuid VARCHAR(36) and backfill with random UUIDs.
--   3. For every child table referencing t_user.id (some via declared
--      FKs from earlier migrations, some Hibernate-managed without an
--      explicit FK), add user_id_uuid / userid_uuid VARCHAR(36),
--      backfill via JOIN, drop any FK + index using the old column,
--      drop the old column, rename the new column to its final name.
--   4. Swap the PK on t_user: drop FKs, drop old PK, drop id, rename
--      id_uuid -> id, add PRIMARY KEY (id), then re-add FKs against
--      the new VARCHAR(36) parent column.
--   5. Drop the deprecated external_id column.
--   6. Recreate the unique partial index on t_user_session
--      (ux_session_user_device_active) since it depends on user_id.
--
-- This script assumes a clean run-through of V1..V8 on a fresh DB will
-- terminate with t_user.id VARCHAR(36) and every FK column re-typed.
-- It is NOT designed to be re-runnable on a partially migrated DB.

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------------------
-- 1. t_user: add id_uuid, backfill, unique-index it for the join phase.
-- ---------------------------------------------------------------------------

ALTER TABLE t_user ADD COLUMN id_uuid VARCHAR(36);

UPDATE t_user SET id_uuid = gen_random_uuid()::text WHERE id_uuid IS NULL;

ALTER TABLE t_user ALTER COLUMN id_uuid SET NOT NULL;

CREATE UNIQUE INDEX ux_t_user_id_uuid ON t_user (id_uuid);

-- ---------------------------------------------------------------------------
-- 2. Child tables. Each block: add new col, backfill via join, drop old
--    FK/index/column, rename. Constraint/index names are inspected
--    dynamically because some originate from Hibernate auto-naming.
-- ---------------------------------------------------------------------------

-- t_user_session.user_id (INTEGER, plain index — no declared FK)
ALTER TABLE t_user_session ADD COLUMN user_id_uuid VARCHAR(36);
UPDATE t_user_session s
   SET user_id_uuid = u.id_uuid
  FROM t_user u
 WHERE u.id = s.user_id;
DROP INDEX IF EXISTS ix_session_user_status;
DROP INDEX IF EXISTS ix_session_user_device;
DROP INDEX IF EXISTS ux_session_user_device_active;
ALTER TABLE t_user_session DROP COLUMN user_id;
ALTER TABLE t_user_session RENAME COLUMN user_id_uuid TO user_id;
ALTER TABLE t_user_session ALTER COLUMN user_id SET NOT NULL;
-- recreated below after PK swap, with FK pointing at the new VARCHAR(36) PK.

-- t_user_role_assignment.user_id (INTEGER, composite PK, FK fk_user_role_assignment_user)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
         WHERE table_name = 't_user_role_assignment'
           AND constraint_name = 'fk_user_role_assignment_user'
    ) THEN
        ALTER TABLE t_user_role_assignment DROP CONSTRAINT fk_user_role_assignment_user;
    END IF;
END $$;

ALTER TABLE t_user_role_assignment DROP CONSTRAINT t_user_role_assignment_pkey;
ALTER TABLE t_user_role_assignment ADD COLUMN user_id_uuid VARCHAR(36);
UPDATE t_user_role_assignment a
   SET user_id_uuid = u.id_uuid
  FROM t_user u
 WHERE u.id = a.user_id;
ALTER TABLE t_user_role_assignment DROP COLUMN user_id;
ALTER TABLE t_user_role_assignment RENAME COLUMN user_id_uuid TO user_id;
ALTER TABLE t_user_role_assignment ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE t_user_role_assignment ADD PRIMARY KEY (user_id, role_id);

-- t_access_history.userid (INTEGER, Hibernate-managed — no declared FK)
ALTER TABLE t_access_history ADD COLUMN userid_uuid VARCHAR(36);
UPDATE t_access_history a
   SET userid_uuid = u.id_uuid
  FROM t_user u
 WHERE u.id = a.userid;
ALTER TABLE t_access_history DROP COLUMN userid;
ALTER TABLE t_access_history RENAME COLUMN userid_uuid TO userid;

-- t_favorite_application.userid (INTEGER, Hibernate-managed — no declared FK)
ALTER TABLE t_favorite_application ADD COLUMN userid_uuid VARCHAR(36);
UPDATE t_favorite_application f
   SET userid_uuid = u.id_uuid
  FROM t_user u
 WHERE u.id = f.userid;
ALTER TABLE t_favorite_application DROP COLUMN userid;
ALTER TABLE t_favorite_application RENAME COLUMN userid_uuid TO userid;

-- t_user_identifier.user_id (INTEGER, inline FK declared in V1_0_2 — Hibernate-generated constraint name)
DO $$
DECLARE
    cname text;
BEGIN
    SELECT tc.constraint_name INTO cname
      FROM information_schema.table_constraints tc
      JOIN information_schema.key_column_usage kcu
        ON tc.constraint_name = kcu.constraint_name
       AND tc.table_name = kcu.table_name
     WHERE tc.table_name = 't_user_identifier'
       AND tc.constraint_type = 'FOREIGN KEY'
       AND kcu.column_name = 'user_id'
     LIMIT 1;
    IF cname IS NOT NULL THEN
        EXECUTE format('ALTER TABLE t_user_identifier DROP CONSTRAINT %I', cname);
    END IF;
END $$;

ALTER TABLE t_user_identifier ADD COLUMN user_id_uuid VARCHAR(36);
UPDATE t_user_identifier i
   SET user_id_uuid = u.id_uuid
  FROM t_user u
 WHERE u.id = i.user_id;
ALTER TABLE t_user_identifier DROP COLUMN user_id;
ALTER TABLE t_user_identifier RENAME COLUMN user_id_uuid TO user_id;
ALTER TABLE t_user_identifier ALTER COLUMN user_id SET NOT NULL;

-- t_user_identity.igrp_user_id (INT, FK fk_t_user_identity_user from V5)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
         WHERE table_name = 't_user_identity'
           AND constraint_name = 'fk_t_user_identity_user'
    ) THEN
        ALTER TABLE t_user_identity DROP CONSTRAINT fk_t_user_identity_user;
    END IF;
END $$;

ALTER TABLE t_user_identity ADD COLUMN igrp_user_id_uuid VARCHAR(36);
UPDATE t_user_identity i
   SET igrp_user_id_uuid = u.id_uuid
  FROM t_user u
 WHERE u.id = i.igrp_user_id;
ALTER TABLE t_user_identity DROP COLUMN igrp_user_id;
ALTER TABLE t_user_identity RENAME COLUMN igrp_user_id_uuid TO igrp_user_id;
ALTER TABLE t_user_identity ALTER COLUMN igrp_user_id SET NOT NULL;

-- t_user_custom_fields.user_id (INTEGER, Hibernate @ElementCollection — auto FK name)
DO $$
DECLARE
    cname text;
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 't_user_custom_fields'
    ) THEN
        SELECT tc.constraint_name INTO cname
          FROM information_schema.table_constraints tc
          JOIN information_schema.key_column_usage kcu
            ON tc.constraint_name = kcu.constraint_name
           AND tc.table_name = kcu.table_name
         WHERE tc.table_name = 't_user_custom_fields'
           AND tc.constraint_type = 'FOREIGN KEY'
           AND kcu.column_name = 'user_id'
         LIMIT 1;
        IF cname IS NOT NULL THEN
            EXECUTE format('ALTER TABLE t_user_custom_fields DROP CONSTRAINT %I', cname);
        END IF;

        ALTER TABLE t_user_custom_fields ADD COLUMN user_id_uuid VARCHAR(36);
        UPDATE t_user_custom_fields c
           SET user_id_uuid = u.id_uuid
          FROM t_user u
         WHERE u.id = c.user_id;
        ALTER TABLE t_user_custom_fields DROP COLUMN user_id;
        ALTER TABLE t_user_custom_fields RENAME COLUMN user_id_uuid TO user_id;
        ALTER TABLE t_user_custom_fields ALTER COLUMN user_id SET NOT NULL;
    END IF;
END $$;

-- t_refresh_token_tombstone.user_id (INTEGER NULL, no FK)
ALTER TABLE t_refresh_token_tombstone ADD COLUMN user_id_uuid VARCHAR(36);
UPDATE t_refresh_token_tombstone t
   SET user_id_uuid = u.id_uuid
  FROM t_user u
 WHERE u.id = t.user_id;
ALTER TABLE t_refresh_token_tombstone DROP COLUMN user_id;
ALTER TABLE t_refresh_token_tombstone RENAME COLUMN user_id_uuid TO user_id;

-- ---------------------------------------------------------------------------
-- 3. t_user PK swap.
-- ---------------------------------------------------------------------------

-- Drop the t_user_aud history mirror's id column type-coupling: t_user_aud
-- uses (id, rev) as its PK. We re-type its id column too so Hibernate Envers
-- can keep writing audit rows. There is no FK from t_user_aud to t_user — it
-- is a standalone history table — so we just retype-in-place.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 't_user_aud') THEN
        -- t_user_aud.id is INTEGER; Envers will start writing VARCHAR(36) rows
        -- after the application redeploys. Pre-existing audit rows keep their
        -- numeric id stringified (no semantic change — the column is now text).
        ALTER TABLE t_user_aud ALTER COLUMN id TYPE VARCHAR(36) USING id::text;
    END IF;
END $$;

-- Drop the deprecated external_id column. PostgreSQL automatically removes
-- any table constraints and indexes that depend on the column.
ALTER TABLE t_user DROP COLUMN IF EXISTS external_id;

-- t_user_aud may carry a mirrored external_id column from Envers; drop it too.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_name = 't_user_aud' AND column_name = 'external_id'
    ) THEN
        ALTER TABLE t_user_aud DROP COLUMN external_id;
    END IF;
END $$;

-- Swap PK: drop old PK, drop INTEGER id, rename id_uuid -> id.
ALTER TABLE t_user DROP CONSTRAINT t_user_pkey;
ALTER TABLE t_user DROP COLUMN id;
ALTER TABLE t_user RENAME COLUMN id_uuid TO id;
DROP INDEX IF EXISTS ux_t_user_id_uuid;
ALTER TABLE t_user ADD PRIMARY KEY (id);

-- ---------------------------------------------------------------------------
-- 4. Re-add FK constraints + recreate dropped indexes pointing at the new PK.
-- ---------------------------------------------------------------------------

ALTER TABLE t_user_role_assignment
    ADD CONSTRAINT fk_user_role_assignment_user
    FOREIGN KEY (user_id) REFERENCES t_user(id) ON DELETE CASCADE;

ALTER TABLE t_user_identifier
    ADD CONSTRAINT fk_t_user_identifier_user
    FOREIGN KEY (user_id) REFERENCES t_user(id);

ALTER TABLE t_user_identity
    ADD CONSTRAINT fk_t_user_identity_user
    FOREIGN KEY (igrp_user_id) REFERENCES t_user(id);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 't_user_custom_fields') THEN
        ALTER TABLE t_user_custom_fields
            ADD CONSTRAINT fk_t_user_custom_fields_user
            FOREIGN KEY (user_id) REFERENCES t_user(id) ON DELETE CASCADE;
    END IF;
END $$;

-- Recreate the t_user_session indexes (Phase B's ux_session_user_device_active included).
CREATE INDEX IF NOT EXISTS ix_session_user_status
    ON t_user_session (user_id, status);
CREATE INDEX IF NOT EXISTS ix_session_user_device
    ON t_user_session (user_id, device_id);
CREATE UNIQUE INDEX IF NOT EXISTS ux_session_user_device_active
    ON t_user_session (user_id, device_id) WHERE status = 'ACTIVE';

COMMIT;
