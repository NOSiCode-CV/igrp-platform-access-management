-- One-time cutover to Jackson 3 (Spring Boot 4 migration).
--
-- OAuth2 authorizations and servlet sessions are stored as JSON written by
-- Jackson 2, which the Jackson 3 readers do not accept. They are removed here,
-- so refresh, introspection and logout answer cleanly (invalid_grant,
-- active=false) instead of failing with a 500 on an unreadable row. Every user
-- logs in again once and M2M clients fetch a new token. Accepted in the
-- migration plan.
--
-- Deploy with no old pods running (strategy: Recreate), and flush the Redis
-- caches as part of the same deployment.

DELETE FROM oauth2_authorization;

DO $$
BEGIN
    IF to_regclass('spring_session') IS NOT NULL THEN
        DELETE FROM spring_session;
    END IF;
END
$$;
