-- 1. Drop existing constraint
ALTER TABLE t_auth_audit_log DROP CONSTRAINT IF EXISTS auth_audit_log_identifier_type_check;
ALTER TABLE t_auth_audit_log DROP CONSTRAINT IF EXISTS t_auth_audit_log_identifier_type_check;

-- 2. Update existing rows
UPDATE t_auth_audit_log SET identifier_type = 'CMDCV' WHERE identifier_type IN ('CMD', 'PHONE');

-- 3. Add new check constraint
ALTER TABLE t_auth_audit_log ADD CONSTRAINT t_auth_audit_log_identifier_type_check 
  CHECK (identifier_type IN ('CNI', 'CMDCV', 'EMAIL', 'UNKNOWN'));
