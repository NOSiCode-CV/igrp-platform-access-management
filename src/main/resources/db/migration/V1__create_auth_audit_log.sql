CREATE TABLE auth_audit_log (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    event_type       VARCHAR(50)  NOT NULL,
    identifier_type  VARCHAR(20)  NOT NULL DEFAULT 'UNKNOWN',
    identifier_value VARCHAR(64),
    user_id          VARCHAR(255),
    application_code VARCHAR(100),
    ip_address       VARCHAR(45),
    user_agent       VARCHAR(512),
    session_id       VARCHAR(255),
    failure_reason   VARCHAR(500),
    timestamp        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    environment      VARCHAR(50),
    CONSTRAINT pk_auth_audit_log PRIMARY KEY (id)
);
CREATE INDEX idx_audit_timestamp        ON auth_audit_log (timestamp DESC);
CREATE INDEX idx_audit_user_timestamp   ON auth_audit_log (user_id, timestamp DESC) WHERE user_id IS NOT NULL;
CREATE INDEX idx_audit_identifier_event ON auth_audit_log (identifier_value, event_type) WHERE identifier_value IS NOT NULL;
