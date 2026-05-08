-- Create t_user_session table for session management
-- Sessions are scoped by (user_id, device_id); per-device active uniqueness is
-- enforced by ux_session_user_device_active.

-- Create table
CREATE TABLE IF NOT EXISTS t_user_session (
  id                  BIGSERIAL PRIMARY KEY,
  session_id          UUID NOT NULL,
  user_id             INTEGER NOT NULL,
  status              VARCHAR(16) NOT NULL,
  started_at          TIMESTAMPTZ NOT NULL,
  last_seen_at        TIMESTAMPTZ NOT NULL,
  expires_at          TIMESTAMPTZ NOT NULL,
  absolute_expires_at TIMESTAMPTZ NULL,
  jti                 VARCHAR(64) NULL,
  client_id           VARCHAR(128) NULL,
  ended_at            TIMESTAMPTZ NULL,
  client_ip           INET NULL,
  user_agent_hash     VARCHAR(64) NULL,
  device_id           VARCHAR(128) NULL,
  closed_reason       VARCHAR(64) NULL,
  closed_by           VARCHAR(32) NULL,
  created_by          VARCHAR(64) NULL,
  created_date        TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_modified_by    VARCHAR(64) NULL,
  last_modified_date  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS ix_session_user_status ON t_user_session(user_id, status);
CREATE INDEX IF NOT EXISTS ix_session_expires_active ON t_user_session(expires_at) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS ix_session_user_device ON t_user_session(user_id, device_id);
CREATE INDEX IF NOT EXISTS ix_session_jti ON t_user_session(jti);

-- Create unique constraints
CREATE UNIQUE INDEX IF NOT EXISTS ux_session_session_id ON t_user_session(session_id);
CREATE UNIQUE INDEX IF NOT EXISTS ux_session_user_device_active ON t_user_session(user_id, device_id) WHERE status = 'ACTIVE';

-- Add comments for documentation
COMMENT ON TABLE t_user_session IS 'Session management table - per-(user, device) active uniqueness';
COMMENT ON COLUMN t_user_session.session_id IS 'Unique session identifier (sid claim in JWT)';
COMMENT ON COLUMN t_user_session.user_id IS 'Internal IGRP user id';
COMMENT ON COLUMN t_user_session.status IS 'Session status: ACTIVE, CLOSED, EXPIRED, REVOKED';
COMMENT ON COLUMN t_user_session.started_at IS 'Session start timestamp';
COMMENT ON COLUMN t_user_session.last_seen_at IS 'Last activity timestamp';
COMMENT ON COLUMN t_user_session.expires_at IS 'Session sliding expiration timestamp';
COMMENT ON COLUMN t_user_session.absolute_expires_at IS 'Absolute lifetime ceiling (refresh cap)';
COMMENT ON COLUMN t_user_session.jti IS 'Latest JWT id bound to this session';
COMMENT ON COLUMN t_user_session.client_id IS 'OAuth2 client that issued the latest token for this session';
COMMENT ON COLUMN t_user_session.ended_at IS 'Session end timestamp (null for active sessions)';
COMMENT ON COLUMN t_user_session.client_ip IS 'Client IP address';
COMMENT ON COLUMN t_user_session.user_agent_hash IS 'Hashed user agent for privacy';
COMMENT ON COLUMN t_user_session.device_id IS 'Device identifier';
COMMENT ON COLUMN t_user_session.closed_reason IS 'Reason for session closure/expiration/revocation';
COMMENT ON COLUMN t_user_session.closed_by IS 'Who closed the session (user, admin, system)';
