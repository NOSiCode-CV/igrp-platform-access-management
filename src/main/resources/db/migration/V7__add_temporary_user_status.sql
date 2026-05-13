-- Phase G3: allow TEMPORARY in t_user.status
-- The Status enum is stored as VARCHAR (@Enumerated(EnumType.STRING)).
-- If a CHECK constraint exists on t_user.status, drop and recreate it to
-- include TEMPORARY. Otherwise this migration is purely additive — the new
-- enum value flows through the existing VARCHAR column.
DO $$
DECLARE
    cname text;
BEGIN
    SELECT conname INTO cname
    FROM pg_constraint
    WHERE conrelid = 't_user'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) ILIKE '%status%';

    IF cname IS NOT NULL THEN
        EXECUTE format('ALTER TABLE t_user DROP CONSTRAINT %I', cname);
    END IF;
END $$;

ALTER TABLE t_user
  ADD CONSTRAINT t_user_status_check
  CHECK (status IN ('TEMPORARY', 'ACTIVE', 'INACTIVE', 'DELETED'));
