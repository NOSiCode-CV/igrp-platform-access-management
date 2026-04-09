-- Add nic and phone_number audit columns
ALTER TABLE t_user_aud
ADD COLUMN IF NOT EXISTS nic VARCHAR(13);
ALTER TABLE t_user_aud
ADD COLUMN IF NOT EXISTS phone_number VARCHAR(32);
-- Add identifier columns to invitation
ALTER TABLE t_invitation_entity
ADD COLUMN IF NOT EXISTS identifier_type VARCHAR(50);
ALTER TABLE t_invitation_entity
ADD COLUMN IF NOT EXISTS identifier_value VARCHAR(255);
ALTER TABLE t_invitation_entity
ADD COLUMN IF NOT EXISTS allowed_auth_methods TEXT;
-- Auth methods collection table
CREATE TABLE IF NOT EXISTS t_invitation_auth_methods (
    invitation_id INTEGER NOT NULL REFERENCES t_invitation_entity(id),
    auth_method VARCHAR(50) NOT NULL
);
-- Roles join table for invitations
CREATE TABLE IF NOT EXISTS t_invitation_roles (
    invitation_id INTEGER NOT NULL REFERENCES t_invitation_entity(id),
    invitation INTEGER NOT NULL REFERENCES t_role(id)
);
-- Audit tables
ALTER TABLE t_invitation_entity_aud
ADD COLUMN IF NOT EXISTS identifier_type VARCHAR(50);
ALTER TABLE t_invitation_entity_aud
ADD COLUMN IF NOT EXISTS identifier_value VARCHAR(255);
ALTER TABLE t_invitation_entity_aud
ADD COLUMN IF NOT EXISTS allowed_auth_methods TEXT;
-- Incluir t_user_identifier e as colunas do t_security_audit_log
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
    PRIMARY KEY (id, rev)
);
CREATE TABLE IF NOT EXISTS t_user_identifier (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES t_user(id),
    type VARCHAR(50) NOT NULL,
    value_normalized VARCHAR(255) NOT NULL,
    verified BOOLEAN DEFAULT FALSE,
    UNIQUE (type, value_normalized)
);
ALTER TABLE t_security_audit_log
ADD COLUMN IF NOT EXISTS decision_reason VARCHAR(255);
ALTER TABLE t_security_audit_log
ADD COLUMN IF NOT EXISTS request_path VARCHAR(255);