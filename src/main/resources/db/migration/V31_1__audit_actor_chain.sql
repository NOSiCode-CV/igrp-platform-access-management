-- V31_1 — Actor chain in audit (v3-20, #28; spec "Token exchange and agents").
--
-- A token exchange names the user in sub and the services acting for them in act.
-- act is never used for authorization; it is recorded here. agent is the current
-- actor (act.sub), or the client of an autonomous service account.
--
-- t_business_audit_event receives act from the starter's outbox (agent was already
-- there); t_security_audit_log gets both for AM's own events, such as the issuance
-- of an exchanged token.
--
-- Idempotent: IF NOT EXISTS.

ALTER TABLE t_business_audit_event ADD COLUMN IF NOT EXISTS act TEXT;
ALTER TABLE t_security_audit_log ADD COLUMN IF NOT EXISTS agent VARCHAR(255);
ALTER TABLE t_security_audit_log ADD COLUMN IF NOT EXISTS act TEXT;
