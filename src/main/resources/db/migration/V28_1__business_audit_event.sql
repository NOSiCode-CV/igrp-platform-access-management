-- V28_1 — The central store of business audit events (v3-22, #32; spec "Audit").
--
-- Every service's events, recorded through the starter (audit.record) and relayed
-- to Kafka topic igrp.audit, consumed by AM. One row per event:
--   id                the event's id, from the service: a duplicate delivery is dropped;
--   service           the source service; each has its own hash chain;
--   sequence_number   the event's place in its service's chain, from 1;
--   payload           the event's payload, as canonical JSON text (numbers kept exact),
--                     so its hash can be recomputed from the row;
--   received_at       when AM stored it; part of the hash too;
--   previous_hash,
--   current_hash      HMAC-SHA256(igrp.audit.chain.secret, previous_hash || fields):
--                     altering any row breaks its service's chain from that row on.
--
-- Append-only, as AM's own audit tables: triggers refuse UPDATE, DELETE and
-- TRUNCATE, and the privileges are revoked from AM's own user.
--
-- Whoever may read the security audit (access-management-api.auth.audit.list)
-- may read the business audit too: the two new keys go to the same roles,
-- departments and service accounts, with the same scope.
--
-- Guarded and idempotent: every statement can run again without changing anything.

CREATE TABLE IF NOT EXISTS t_business_audit_event (
    id              UUID           PRIMARY KEY,
    service         VARCHAR(128)   NOT NULL,
    sequence_number BIGINT         NOT NULL,
    event           VARCHAR(128)   NOT NULL,
    resource        VARCHAR(512)   NOT NULL,
    payload         TEXT,
    username        VARCHAR(255)   NOT NULL,
    agent           VARCHAR(255),
    result          VARCHAR(32)    NOT NULL,
    trace_id        VARCHAR(64)    NOT NULL,
    occurred_at     TIMESTAMPTZ(6) NOT NULL,
    received_at     TIMESTAMPTZ(6) NOT NULL,
    previous_hash   VARCHAR(64)    NOT NULL,
    current_hash    VARCHAR(64)    NOT NULL,
    CONSTRAINT uk_business_audit_event_chain UNIQUE (service, sequence_number)
);

CREATE INDEX IF NOT EXISTS ix_business_audit_event_username ON t_business_audit_event (username, occurred_at);
CREATE INDEX IF NOT EXISTS ix_business_audit_event_occurred ON t_business_audit_event (occurred_at);
CREATE INDEX IF NOT EXISTS ix_business_audit_event_trace ON t_business_audit_event (trace_id);

CREATE OR REPLACE FUNCTION trg_business_audit_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 't_business_audit_event is append-only (%)', TG_OP;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS no_update_delete_business_audit ON t_business_audit_event;
CREATE TRIGGER no_update_delete_business_audit
    BEFORE UPDATE OR DELETE ON t_business_audit_event
    FOR EACH ROW EXECUTE FUNCTION trg_business_audit_append_only();

DROP TRIGGER IF EXISTS no_truncate_business_audit ON t_business_audit_event;
CREATE TRIGGER no_truncate_business_audit
    BEFORE TRUNCATE ON t_business_audit_event
    FOR EACH STATEMENT EXECUTE FUNCTION trg_business_audit_append_only();

REVOKE UPDATE, DELETE, TRUNCATE ON t_business_audit_event FROM PUBLIC, CURRENT_USER;

DO $$
DECLARE
    app_id  INTEGER;
    reader  INTEGER;
    new_key TEXT;
    key_id  INTEGER;
BEGIN
    SELECT id INTO app_id FROM t_application WHERE code = 'APP_IGRP_CENTER' AND status <> 'DELETED';
    SELECT id INTO reader FROM t_permission
     WHERE permission_key = 'access-management-api.auth.audit.list' AND status <> 'DELETED';
    IF app_id IS NULL OR reader IS NULL THEN
        RAISE NOTICE 'No holder of auth.audit.list (fresh database): no business audit key to give in V28_1.';
        RETURN;
    END IF;
    FOREACH new_key IN ARRAY ARRAY['access-management-api.auth.audit.business.list',
                                   'access-management-api.auth.audit.business.validate.list'] LOOP
        INSERT INTO t_permission (permission_key, name, status, service, application_id,
                                  created_by, created_date, last_modified_by, last_modified_date)
        SELECT new_key, new_key, 'ACTIVE', 'access-management-api', app_id, 'system', now(), 'system', now()
         WHERE NOT EXISTS (SELECT 1 FROM t_permission p WHERE p.permission_key = new_key);
        SELECT id INTO key_id FROM t_permission WHERE permission_key = new_key;

        INSERT INTO t_role_permission (role_id, permission, scope)
        SELECT rp.role_id, key_id, rp.scope FROM t_role_permission rp
         WHERE rp.permission = reader
           AND NOT EXISTS (SELECT 1 FROM t_role_permission e WHERE e.role_id = rp.role_id AND e.permission = key_id);
        INSERT INTO t_permission_department (permission_id, department)
        SELECT key_id, pd.department FROM t_permission_department pd
         WHERE pd.permission_id = reader
           AND NOT EXISTS (SELECT 1 FROM t_permission_department e
                            WHERE e.permission_id = key_id AND e.department = pd.department);
        INSERT INTO t_service_account_permission_grant (permission_id, service_account_id, granted_at)
        SELECT key_id, g.service_account_id, now() FROM t_service_account_permission_grant g
         WHERE g.permission_id = reader
           AND NOT EXISTS (SELECT 1 FROM t_service_account_permission_grant e
                            WHERE e.permission_id = key_id AND e.service_account_id = g.service_account_id);
    END LOOP;
END $$;
