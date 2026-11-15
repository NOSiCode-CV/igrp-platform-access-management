-- V27_1 — Migration review suggestions (v3-29, #37; spec "Database migration").
--
-- Business permissions are never converted automatically. When a service
-- registers a route with its legacyKey (the permission its 0.2 @PreAuthorize
-- checked), and a live legacy permission with that name has assignments, AM
-- suggests copying them to the route key. An administrator confirms (the
-- roles, departments and scopes are copied) or dismisses each suggestion in
-- App Center's migration review.
--
-- Guarded and idempotent: every statement can run again without changing anything.

CREATE TABLE IF NOT EXISTS t_migration_suggestion (
    id                   SERIAL PRIMARY KEY,
    permission_id        INTEGER        NOT NULL REFERENCES t_permission (id) ON DELETE CASCADE,
    legacy_permission_id INTEGER        NOT NULL REFERENCES t_permission (id) ON DELETE CASCADE,
    status               VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    created_date         TIMESTAMPTZ(6) NOT NULL DEFAULT now(),
    decided_date         TIMESTAMPTZ(6),
    decided_by           VARCHAR(255),
    CONSTRAINT uk_migration_suggestion UNIQUE (permission_id, legacy_permission_id),
    CONSTRAINT ck_migration_suggestion_status CHECK (status IN ('PENDING', 'CONFIRMED', 'DISMISSED'))
);

CREATE INDEX IF NOT EXISTS ix_migration_suggestion_status ON t_migration_suggestion (status);
