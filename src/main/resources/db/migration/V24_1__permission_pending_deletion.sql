-- V24_1 — Version-gated deletion of route keys (v3-03, #11).
--
-- A registration from a service's newest version schedules the keys it no
-- longer declares for deletion after a grace period, so a rolling deploy
-- never deletes and recreates keys back and forth. A job deletes the due rows,
-- claiming each with FOR UPDATE SKIP LOCKED so several AM replicas never delete
-- the same key twice. A registration that declares a key again removes its row.
--
-- Idempotent: CREATE ... IF NOT EXISTS throughout.

CREATE TABLE IF NOT EXISTS t_permission_pending_deletion (
    permission_id INTEGER                     NOT NULL REFERENCES t_permission (id) ON DELETE CASCADE,
    service       VARCHAR(120)                NOT NULL,
    version       VARCHAR(64)                 NOT NULL,
    due_at        TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    scheduled_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT pk_t_permission_pending_deletion PRIMARY KEY (permission_id)
);

CREATE INDEX IF NOT EXISTS ix_t_permission_pending_deletion_due ON t_permission_pending_deletion (due_at);
CREATE INDEX IF NOT EXISTS ix_t_permission_pending_deletion_service ON t_permission_pending_deletion (service);
