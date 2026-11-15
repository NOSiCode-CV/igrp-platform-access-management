-- V26_1 — Application pages and their permissions (v3-26, #34; spec "Frontend").
--
-- A frontend page, as its access manifest declares it (#35 registers them):
--   page_key           app.<application>.<page>; unique;
--   application_id     the application it belongs to;
--   path               its Next.js App Router path, e.g. /settings/users/[id]
--                      (route groups, [param], [...param] and [[...param]]);
--   status             ACTIVE, or DELETED once its manifest is gone;
--   last_seen_version  the newest frontend version that declared it.
-- t_application_page_permission lists the keys it needs: the required ones,
-- all checked by POST /api/authorize/page, and the optional ones (with a label)
-- an administrator may grant with the page.
--
-- Guarded and idempotent: every statement can run again without changing anything.

CREATE TABLE IF NOT EXISTS t_application_page (
    id                 SERIAL PRIMARY KEY,
    page_key           VARCHAR(255) NOT NULL,
    application_id     INTEGER      NOT NULL REFERENCES t_application (id) ON DELETE CASCADE,
    path               VARCHAR(512) NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    last_seen_version  VARCHAR(64),
    created_date       TIMESTAMPTZ(6) NOT NULL DEFAULT now(),
    last_modified_date TIMESTAMPTZ(6) NOT NULL DEFAULT now(),
    CONSTRAINT uk_application_page_key UNIQUE (page_key),
    CONSTRAINT ck_application_page_status CHECK (status IN ('ACTIVE', 'DELETED'))
);

CREATE INDEX IF NOT EXISTS ix_application_page_application ON t_application_page (application_id, status);

-- One live page per application and path, so a path never has two answers.
CREATE UNIQUE INDEX IF NOT EXISTS uk_application_page_live_path
    ON t_application_page (application_id, path) WHERE status = 'ACTIVE';

CREATE TABLE IF NOT EXISTS t_application_page_permission (
    page_id        INTEGER      NOT NULL REFERENCES t_application_page (id) ON DELETE CASCADE,
    permission_key VARCHAR(255) NOT NULL,
    required       BOOLEAN      NOT NULL,
    label          VARCHAR(255),
    PRIMARY KEY (page_id, permission_key)
);
