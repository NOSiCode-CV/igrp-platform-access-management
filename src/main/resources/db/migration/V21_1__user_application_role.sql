-- V21_1 — Active role per application (v3-05, #13).
--
-- A user acts under one role per application, remembered across logins. The
-- row holds the user's explicit choice; with no row, AM falls back to the
-- user's only valid role for the application (or to none when several are
-- valid). The global t_user.active_role_id stays for the 0.2 surface until #38.
--
-- Idempotent: CREATE ... IF NOT EXISTS throughout. The foreign keys cascade on
-- user, application and role deletion, so a stale choice never blocks a delete.

CREATE TABLE IF NOT EXISTS t_user_application_role (
    user_id        VARCHAR(36)                 NOT NULL REFERENCES t_user (id) ON DELETE CASCADE,
    application_id INTEGER                     NOT NULL REFERENCES t_application (id) ON DELETE CASCADE,
    role_id        INTEGER                     NOT NULL REFERENCES t_role (id) ON DELETE CASCADE,
    updated_at     TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT pk_t_user_application_role PRIMARY KEY (user_id, application_id)
);

CREATE INDEX IF NOT EXISTS ix_t_user_application_role_role ON t_user_application_role (role_id);
