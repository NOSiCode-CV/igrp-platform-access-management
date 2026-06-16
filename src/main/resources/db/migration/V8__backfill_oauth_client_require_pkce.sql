-- V8 — backfill t_oauth_client.require_pkce.
--
-- The OWASP A01 PKCE remediation (commit 3caa8d44) added a `require_pkce`
-- field to OAuthClientEntity as a primitive boolean with default true and
-- @Column(nullable = false). On clusters where the table already existed,
-- Hibernate's ddl-auto=update added the column as NULLABLE (update never
-- back-tightens existing tables) so the pre-existing rows ended up with
-- require_pkce = NULL. On the next read Hibernate tried to unbox NULL into
-- a primitive boolean and crashed with:
--
--   NullPointerException: Cannot invoke "java.lang.Boolean.booleanValue()"
--   because "<parameter2>[...]" is null
--
-- This migration:
--   1. Ensures the column exists (covers fresh DBs where Hibernate didn't
--      get a chance to add it yet — order is Flyway → Hibernate).
--   2. Backfills NULL rows to TRUE (the entity-level default).
--   3. Pins NOT NULL + DEFAULT TRUE at the DB level so future inserts that
--      bypass the entity (manual SQL, ad-hoc tooling) still produce valid
--      rows.

DO $$
BEGIN
    IF to_regclass('public.t_oauth_client') IS NOT NULL THEN
        ALTER TABLE t_oauth_client
            ADD COLUMN IF NOT EXISTS require_pkce BOOLEAN;

        UPDATE t_oauth_client
            SET require_pkce = TRUE
            WHERE require_pkce IS NULL;

        ALTER TABLE t_oauth_client
            ALTER COLUMN require_pkce SET DEFAULT TRUE;

        ALTER TABLE t_oauth_client
            ALTER COLUMN require_pkce SET NOT NULL;
    END IF;
END $$;
