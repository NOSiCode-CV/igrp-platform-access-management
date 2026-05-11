-- PostgreSQL-flavored schema for Spring Authorization Server's
-- JdbcOAuth2AuthorizationService. Mirrors oauth2-authorization-schema.sql
-- shipped in spring-security-oauth2-authorization-server, with BLOB columns
-- mapped to BYTEA. Idempotent — safe to execute on every boot.

CREATE TABLE IF NOT EXISTS oauth2_authorization (
    id                              varchar(100) NOT NULL,
    registered_client_id            varchar(100) NOT NULL,
    principal_name                  varchar(200) NOT NULL,
    authorization_grant_type        varchar(100) NOT NULL,
    authorized_scopes               varchar(1000) DEFAULT NULL,
    attributes                      bytea DEFAULT NULL,
    state                           varchar(500) DEFAULT NULL,
    authorization_code_value        bytea DEFAULT NULL,
    authorization_code_issued_at    timestamp DEFAULT NULL,
    authorization_code_expires_at   timestamp DEFAULT NULL,
    authorization_code_metadata     bytea DEFAULT NULL,
    access_token_value              bytea DEFAULT NULL,
    access_token_issued_at          timestamp DEFAULT NULL,
    access_token_expires_at         timestamp DEFAULT NULL,
    access_token_metadata           bytea DEFAULT NULL,
    access_token_type               varchar(100) DEFAULT NULL,
    access_token_scopes             varchar(1000) DEFAULT NULL,
    oidc_id_token_value             bytea DEFAULT NULL,
    oidc_id_token_issued_at         timestamp DEFAULT NULL,
    oidc_id_token_expires_at        timestamp DEFAULT NULL,
    oidc_id_token_metadata          bytea DEFAULT NULL,
    refresh_token_value             bytea DEFAULT NULL,
    refresh_token_issued_at         timestamp DEFAULT NULL,
    refresh_token_expires_at        timestamp DEFAULT NULL,
    refresh_token_metadata          bytea DEFAULT NULL,
    user_code_value                 bytea DEFAULT NULL,
    user_code_issued_at             timestamp DEFAULT NULL,
    user_code_expires_at            timestamp DEFAULT NULL,
    user_code_metadata              bytea DEFAULT NULL,
    device_code_value               bytea DEFAULT NULL,
    device_code_issued_at           timestamp DEFAULT NULL,
    device_code_expires_at          timestamp DEFAULT NULL,
    device_code_metadata            bytea DEFAULT NULL,
    PRIMARY KEY (id)
);

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

CREATE TABLE IF NOT EXISTS oauth2_authorization_consent (
    registered_client_id varchar(100) NOT NULL,
    principal_name       varchar(200) NOT NULL,
    authorities          varchar(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
);
