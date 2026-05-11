-- Spring Authorization Server runtime tables.
-- These names intentionally follow the framework defaults because
-- JdbcOAuth2AuthorizationService expects them.

CREATE TABLE IF NOT EXISTS oauth2_authorization (
    id                              varchar(100) NOT NULL,
    registered_client_id            varchar(100) NOT NULL,
    principal_name                  varchar(200) NOT NULL,
    authorization_grant_type        varchar(100) NOT NULL,
    authorized_scopes               varchar(1000) DEFAULT NULL,
    attributes                      text DEFAULT NULL,
    state                           varchar(500) DEFAULT NULL,
    authorization_code_value        text DEFAULT NULL,
    authorization_code_issued_at    timestamp DEFAULT NULL,
    authorization_code_expires_at   timestamp DEFAULT NULL,
    authorization_code_metadata     text DEFAULT NULL,
    access_token_value              text DEFAULT NULL,
    access_token_issued_at          timestamp DEFAULT NULL,
    access_token_expires_at         timestamp DEFAULT NULL,
    access_token_metadata           text DEFAULT NULL,
    access_token_type               varchar(100) DEFAULT NULL,
    access_token_scopes             varchar(1000) DEFAULT NULL,
    oidc_id_token_value             text DEFAULT NULL,
    oidc_id_token_issued_at         timestamp DEFAULT NULL,
    oidc_id_token_expires_at        timestamp DEFAULT NULL,
    oidc_id_token_metadata          text DEFAULT NULL,
    refresh_token_value             text DEFAULT NULL,
    refresh_token_issued_at         timestamp DEFAULT NULL,
    refresh_token_expires_at        timestamp DEFAULT NULL,
    refresh_token_metadata          text DEFAULT NULL,
    user_code_value                 text DEFAULT NULL,
    user_code_issued_at             timestamp DEFAULT NULL,
    user_code_expires_at            timestamp DEFAULT NULL,
    user_code_metadata              text DEFAULT NULL,
    device_code_value               text DEFAULT NULL,
    device_code_issued_at           timestamp DEFAULT NULL,
    device_code_expires_at          timestamp DEFAULT NULL,
    device_code_metadata            text DEFAULT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS oauth2_authorization_consent (
    registered_client_id varchar(100) NOT NULL,
    principal_name       varchar(200) NOT NULL,
    authorities          varchar(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'oauth2_authorization' AND column_name = 'attributes' AND data_type = 'bytea'
    ) THEN
        ALTER TABLE oauth2_authorization ALTER COLUMN attributes TYPE text
            USING CASE WHEN attributes IS NULL THEN NULL ELSE convert_from(attributes, 'UTF8') END;
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'oauth2_authorization' AND column_name = 'authorization_code_value' AND data_type = 'bytea'
    ) THEN
        ALTER TABLE oauth2_authorization ALTER COLUMN authorization_code_value TYPE text
            USING CASE WHEN authorization_code_value IS NULL THEN NULL ELSE convert_from(authorization_code_value, 'UTF8') END;
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'oauth2_authorization' AND column_name = 'authorization_code_metadata' AND data_type = 'bytea'
    ) THEN
        ALTER TABLE oauth2_authorization ALTER COLUMN authorization_code_metadata TYPE text
            USING CASE WHEN authorization_code_metadata IS NULL THEN NULL ELSE convert_from(authorization_code_metadata, 'UTF8') END;
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'oauth2_authorization' AND column_name = 'access_token_value' AND data_type = 'bytea'
    ) THEN
        ALTER TABLE oauth2_authorization ALTER COLUMN access_token_value TYPE text
            USING CASE WHEN access_token_value IS NULL THEN NULL ELSE convert_from(access_token_value, 'UTF8') END;
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'oauth2_authorization' AND column_name = 'access_token_metadata' AND data_type = 'bytea'
    ) THEN
        ALTER TABLE oauth2_authorization ALTER COLUMN access_token_metadata TYPE text
            USING CASE WHEN access_token_metadata IS NULL THEN NULL ELSE convert_from(access_token_metadata, 'UTF8') END;
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'oauth2_authorization' AND column_name = 'oidc_id_token_value' AND data_type = 'bytea'
    ) THEN
        ALTER TABLE oauth2_authorization ALTER COLUMN oidc_id_token_value TYPE text
            USING CASE WHEN oidc_id_token_value IS NULL THEN NULL ELSE convert_from(oidc_id_token_value, 'UTF8') END;
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'oauth2_authorization' AND column_name = 'oidc_id_token_metadata' AND data_type = 'bytea'
    ) THEN
        ALTER TABLE oauth2_authorization ALTER COLUMN oidc_id_token_metadata TYPE text
            USING CASE WHEN oidc_id_token_metadata IS NULL THEN NULL ELSE convert_from(oidc_id_token_metadata, 'UTF8') END;
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'oauth2_authorization' AND column_name = 'refresh_token_value' AND data_type = 'bytea'
    ) THEN
        ALTER TABLE oauth2_authorization ALTER COLUMN refresh_token_value TYPE text
            USING CASE WHEN refresh_token_value IS NULL THEN NULL ELSE convert_from(refresh_token_value, 'UTF8') END;
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'oauth2_authorization' AND column_name = 'refresh_token_metadata' AND data_type = 'bytea'
    ) THEN
        ALTER TABLE oauth2_authorization ALTER COLUMN refresh_token_metadata TYPE text
            USING CASE WHEN refresh_token_metadata IS NULL THEN NULL ELSE convert_from(refresh_token_metadata, 'UTF8') END;
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'oauth2_authorization' AND column_name = 'user_code_value' AND data_type = 'bytea'
    ) THEN
        ALTER TABLE oauth2_authorization ALTER COLUMN user_code_value TYPE text
            USING CASE WHEN user_code_value IS NULL THEN NULL ELSE convert_from(user_code_value, 'UTF8') END;
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'oauth2_authorization' AND column_name = 'user_code_metadata' AND data_type = 'bytea'
    ) THEN
        ALTER TABLE oauth2_authorization ALTER COLUMN user_code_metadata TYPE text
            USING CASE WHEN user_code_metadata IS NULL THEN NULL ELSE convert_from(user_code_metadata, 'UTF8') END;
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'oauth2_authorization' AND column_name = 'device_code_value' AND data_type = 'bytea'
    ) THEN
        ALTER TABLE oauth2_authorization ALTER COLUMN device_code_value TYPE text
            USING CASE WHEN device_code_value IS NULL THEN NULL ELSE convert_from(device_code_value, 'UTF8') END;
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'oauth2_authorization' AND column_name = 'device_code_metadata' AND data_type = 'bytea'
    ) THEN
        ALTER TABLE oauth2_authorization ALTER COLUMN device_code_metadata TYPE text
            USING CASE WHEN device_code_metadata IS NULL THEN NULL ELSE convert_from(device_code_metadata, 'UTF8') END;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS ix_oauth2_authorization_state
    ON oauth2_authorization (state);
CREATE INDEX IF NOT EXISTS ix_oauth2_authorization_authorization_code_value
    ON oauth2_authorization (authorization_code_value);
CREATE INDEX IF NOT EXISTS ix_oauth2_authorization_access_token_value
    ON oauth2_authorization (access_token_value);
CREATE INDEX IF NOT EXISTS ix_oauth2_authorization_oidc_id_token_value
    ON oauth2_authorization (oidc_id_token_value);
CREATE INDEX IF NOT EXISTS ix_oauth2_authorization_refresh_token_value
    ON oauth2_authorization (refresh_token_value);
CREATE INDEX IF NOT EXISTS ix_oauth2_authorization_user_code_value
    ON oauth2_authorization (user_code_value);
CREATE INDEX IF NOT EXISTS ix_oauth2_authorization_device_code_value
    ON oauth2_authorization (device_code_value);
