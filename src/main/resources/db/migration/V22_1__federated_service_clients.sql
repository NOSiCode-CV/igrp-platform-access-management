-- V22_1 — federated service clients (v3-17, #24).
--
-- A service client (grant client_credentials only) now lives in the IdP: AM
-- keeps its client_id, application and service account, plus the IdP it lives
-- in (`idp`), and never its secret. Login clients of the frontends keep their
-- secret hash in AM, so client_secret becomes nullable instead of dropped.
-- Existing rows are untouched: they stay AM-local clients (idp NULL).

DO $$
BEGIN
    IF to_regclass('public.t_oauth_client') IS NOT NULL THEN
        ALTER TABLE t_oauth_client
            ADD COLUMN IF NOT EXISTS idp VARCHAR(40);

        ALTER TABLE t_oauth_client
            ALTER COLUMN client_secret DROP NOT NULL;

        -- A row has either a secret in AM or an IdP, never both, never neither.
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_oauth_client_secret_or_idp') THEN
            ALTER TABLE t_oauth_client
                ADD CONSTRAINT ck_oauth_client_secret_or_idp
                CHECK ((client_secret IS NULL) <> (idp IS NULL));
        END IF;
    END IF;
END $$;
