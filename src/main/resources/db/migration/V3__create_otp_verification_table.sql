CREATE TABLE IF NOT EXISTS t_otp_verification (
    id BIGSERIAL PRIMARY KEY,
    reference_id VARCHAR(255) NOT NULL,
    otp_code VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE t_invitation_entity
ADD COLUMN IF NOT EXISTS otp_id BIGINT REFERENCES t_otp_verification(id);

ALTER TABLE t_invitation_entity_aud
ADD COLUMN IF NOT EXISTS otp_id BIGINT;
