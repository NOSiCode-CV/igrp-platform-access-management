CREATE TABLE IF NOT EXISTS t_user_session (
    id BIGSERIAL PRIMARY KEY,
    session_id UUID NOT NULL,
    user_external_id VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ NULL,
    client_ip INET NULL,
    user_agent_hash VARCHAR(64) NULL,
    device_id VARCHAR(128) NULL,
    closed_reason VARCHAR(64) NULL,
    closed_by VARCHAR(32) NULL,
    created_by VARCHAR(64) NULL,
    created_date TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_modified_by VARCHAR(64) NULL,
    last_modified_date TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_session_user_status ON t_user_session(user_external_id, status);
CREATE INDEX IF NOT EXISTS ix_session_expires_active ON t_user_session(expires_at) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX IF NOT EXISTS ux_session_session_id ON t_user_session(session_id);
CREATE UNIQUE INDEX IF NOT EXISTS ux_one_active_session_per_user ON t_user_session(user_external_id) WHERE status = 'ACTIVE';

CREATE TABLE IF NOT EXISTS t_auth_audit_log (
    id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    identifier_type VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    identifier_value VARCHAR(64),
    user_id VARCHAR(255),
    application_code VARCHAR(100),
    ip_address VARCHAR(45),
    user_agent VARCHAR(512),
    session_id VARCHAR(255),
    failure_reason VARCHAR(500),
    timestamp TIMESTAMPTZ NOT NULL DEFAULT now(),
    environment VARCHAR(50),
    CONSTRAINT pk_t_auth_audit_log_v5 PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON t_auth_audit_log (timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_audit_user_timestamp ON t_auth_audit_log (user_id, timestamp DESC) WHERE user_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_audit_identifier_event ON t_auth_audit_log (identifier_value, event_type) WHERE identifier_value IS NOT NULL;

CREATE TABLE IF NOT EXISTS t_otp_verification (
    id BIGSERIAL PRIMARY KEY,
    reference_id VARCHAR(255) NOT NULL,
    otp_code VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE t_invitation_entity ADD COLUMN IF NOT EXISTS otp_id BIGINT;
ALTER TABLE t_invitation_entity_aud ADD COLUMN IF NOT EXISTS otp_id BIGINT;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_name = 't_invitation_entity'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_name = 't_invitation_entity'
          AND constraint_name = 'fk_t_invitation_entity_otp_id'
    ) THEN
        ALTER TABLE t_invitation_entity
            ADD CONSTRAINT fk_t_invitation_entity_otp_id
            FOREIGN KEY (otp_id) REFERENCES t_otp_verification(id);
    END IF;
END $$;

ALTER TABLE t_user ADD COLUMN IF NOT EXISTS nic VARCHAR(13);
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS phone_number VARCHAR(32);
CREATE INDEX IF NOT EXISTS idx_user_nic ON t_user (nic) WHERE nic IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_user_phone ON t_user (phone_number) WHERE phone_number IS NOT NULL;

ALTER TABLE t_user_aud ADD COLUMN IF NOT EXISTS nic VARCHAR(13);
ALTER TABLE t_user_aud ADD COLUMN IF NOT EXISTS phone_number VARCHAR(32);

ALTER TABLE t_invitation_entity ADD COLUMN IF NOT EXISTS identifier_type VARCHAR(50);
ALTER TABLE t_invitation_entity ADD COLUMN IF NOT EXISTS identifier_value VARCHAR(255);
ALTER TABLE t_invitation_entity ADD COLUMN IF NOT EXISTS allowed_auth_methods TEXT;

CREATE TABLE IF NOT EXISTS t_invitation_auth_methods (
    invitation_id INTEGER NOT NULL REFERENCES t_invitation_entity(id),
    auth_method VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS t_invitation_roles (
    invitation_id INTEGER NOT NULL REFERENCES t_invitation_entity(id),
    invitation INTEGER NOT NULL REFERENCES t_role(id)
);

CREATE TABLE IF NOT EXISTS t_invitation_entity_aud (
    id INTEGER NOT NULL,
    rev INTEGER NOT NULL,
    revtype SMALLINT,
    created_by VARCHAR(255),
    created_date TIMESTAMP,
    last_modified_by VARCHAR(255),
    last_modified_date TIMESTAMP,
    comments VARCHAR(255),
    expiry TIMESTAMP,
    status VARCHAR(255),
    token VARCHAR(255),
    identifier_type VARCHAR(50),
    identifier_value VARCHAR(255),
    allowed_auth_methods TEXT,
    otp_id BIGINT,
    PRIMARY KEY (id, rev)
);

CREATE TABLE IF NOT EXISTS t_user_role_assignment (
    user_id INT NOT NULL,
    role_id INT NOT NULL,
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NULL,
    PRIMARY KEY (user_id, role_id)
);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_name = 't_user'
    ) AND EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_name = 't_role'
    ) THEN
        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.table_constraints
            WHERE table_name = 't_user_role_assignment'
              AND constraint_name = 'fk_user_role_assignment_user'
        ) THEN
            ALTER TABLE t_user_role_assignment
                ADD CONSTRAINT fk_user_role_assignment_user
                FOREIGN KEY (user_id) REFERENCES t_user(id) ON DELETE CASCADE;
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.table_constraints
            WHERE table_name = 't_user_role_assignment'
              AND constraint_name = 'fk_user_role_assignment_role'
        ) THEN
            ALTER TABLE t_user_role_assignment
                ADD CONSTRAINT fk_user_role_assignment_role
                FOREIGN KEY (role_id) REFERENCES t_role(id) ON DELETE CASCADE;
        END IF;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_name = 't_role_users'
    ) THEN
        INSERT INTO t_user_role_assignment (user_id, role_id, assigned_at, expires_at)
        SELECT ru.users_id, ru.role_entity_id, CURRENT_TIMESTAMP, NULL
        FROM t_role_users ru
        WHERE NOT EXISTS (
            SELECT 1
            FROM t_user_role_assignment ura
            WHERE ura.user_id = ru.users_id
              AND ura.role_id = ru.role_entity_id
        );
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS t_oauth_client (
    id UUID PRIMARY KEY,
    client_id VARCHAR(120) NOT NULL,
    client_secret TEXT NOT NULL,
    client_name VARCHAR(180),
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    access_token_ttl INT NOT NULL,
    refresh_token_ttl INT NOT NULL,
    authorization_code_ttl INT NOT NULL,
    application_id INT NULL,
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_oauth_client_client_id ON t_oauth_client (client_id);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_name = 't_application'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_name = 't_oauth_client'
          AND constraint_name = 'fk_t_oauth_client_application'
    ) THEN
        ALTER TABLE t_oauth_client
            ADD CONSTRAINT fk_t_oauth_client_application
            FOREIGN KEY (application_id) REFERENCES t_application(id);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS t_oauth_client_scope (
    client_id UUID NOT NULL REFERENCES t_oauth_client(id) ON DELETE CASCADE,
    scope VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS t_oauth_client_redirect_uri (
    client_id UUID NOT NULL REFERENCES t_oauth_client(id) ON DELETE CASCADE,
    redirect_uri VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS t_oauth_client_grant_type (
    client_id UUID NOT NULL REFERENCES t_oauth_client(id) ON DELETE CASCADE,
    grant_type VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS t_oauth_auth_audit_log (
    id UUID PRIMARY KEY,
    username VARCHAR(255),
    event_type VARCHAR(40) NOT NULL,
    ip_address VARCHAR(64),
    user_agent VARCHAR(500),
    session_id VARCHAR(120),
    timestamp TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_auth_audit_username ON t_oauth_auth_audit_log (username);
CREATE INDEX IF NOT EXISTS idx_auth_audit_event_type ON t_oauth_auth_audit_log (event_type);

CREATE TABLE IF NOT EXISTS t_user_identity (
    id UUID PRIMARY KEY,
    provider VARCHAR(80) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    connection VARCHAR(120),
    igrp_user_id INT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_identity_provider_sub ON t_user_identity (provider, user_id);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_name = 't_user'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_name = 't_user_identity'
          AND constraint_name = 'fk_t_user_identity_user'
    ) THEN
        ALTER TABLE t_user_identity
            ADD CONSTRAINT fk_t_user_identity_user
            FOREIGN KEY (igrp_user_id) REFERENCES t_user(id);
    END IF;
END $$;
