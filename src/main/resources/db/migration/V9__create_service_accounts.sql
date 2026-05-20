CREATE TABLE IF NOT EXISTS t_service_account (
    id UUID PRIMARY KEY,
    name VARCHAR(180) NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    oauth_client_id UUID NOT NULL,
    application_id INT NULL,
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_service_account_oauth_client
    ON t_service_account (oauth_client_id);

CREATE INDEX IF NOT EXISTS idx_service_account_active
    ON t_service_account (active);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_name = 't_service_account'
          AND constraint_name = 'fk_service_account_oauth_client'
    ) THEN
        ALTER TABLE t_service_account
            ADD CONSTRAINT fk_service_account_oauth_client
            FOREIGN KEY (oauth_client_id) REFERENCES t_oauth_client(id)
            ON DELETE CASCADE;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_name = 't_application'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_name = 't_service_account'
          AND constraint_name = 'fk_service_account_application'
    ) THEN
        ALTER TABLE t_service_account
            ADD CONSTRAINT fk_service_account_application
            FOREIGN KEY (application_id) REFERENCES t_application(id);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS t_service_account_role_assignment (
    service_account_id UUID NOT NULL,
    role_id INT NOT NULL,
    assigned_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP NULL,
    PRIMARY KEY (service_account_id, role_id)
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_name = 't_service_account_role_assignment'
          AND constraint_name = 'fk_service_account_role_assignment_account'
    ) THEN
        ALTER TABLE t_service_account_role_assignment
            ADD CONSTRAINT fk_service_account_role_assignment_account
            FOREIGN KEY (service_account_id) REFERENCES t_service_account(id)
            ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_name = 't_service_account_role_assignment'
          AND constraint_name = 'fk_service_account_role_assignment_role'
    ) THEN
        ALTER TABLE t_service_account_role_assignment
            ADD CONSTRAINT fk_service_account_role_assignment_role
            FOREIGN KEY (role_id) REFERENCES t_role(id)
            ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_service_account_role_assignment_role
    ON t_service_account_role_assignment (role_id);
