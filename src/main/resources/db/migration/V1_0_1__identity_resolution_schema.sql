-- Add nic and phone_number to t_user
ALTER TABLE t_user
ADD COLUMN IF NOT EXISTS nic VARCHAR(13);
ALTER TABLE t_user
ADD COLUMN IF NOT EXISTS phone_number VARCHAR(32);
CREATE INDEX IF NOT EXISTS idx_user_nic ON t_user (nic)
WHERE nic IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_user_phone ON t_user (phone_number)
WHERE phone_number IS NOT NULL;
-- Mirror columns to audit table
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
-- Auth methods collection table for invitations
CREATE TABLE IF NOT EXISTS t_invitation_auth_methods (
    invitation_id INTEGER NOT NULL REFERENCES t_invitation_entity(id),
    auth_method VARCHAR(50) NOT NULL
);
-- Roles join table for invitations
CREATE TABLE IF NOT EXISTS t_invitation_roles (
    invitation_id INTEGER NOT NULL REFERENCES t_invitation_entity(id),
    invitation INTEGER NOT NULL REFERENCES t_role(id)
);
-- Audit table for invitations
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