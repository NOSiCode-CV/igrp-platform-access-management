-- 1. Drop existing constraint
ALTER TABLE auth_audit_log DROP CONSTRAINT auth_audit_log_identifier_type_check;

-- 2. Update existing rows
UPDATE auth_audit_log SET identifier_type = 'CMDCV' WHERE identifier_type = 'CMD';

-- 3. Add new check constraint
ALTER TABLE auth_audit_log ADD CONSTRAINT auth_audit_log_identifier_type_check 
  CHECK (identifier_type IN ('CNI', 'CMDCV', 'EMAIL', 'UNKNOWN'));
