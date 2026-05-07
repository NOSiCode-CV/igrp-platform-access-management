package cv.igrp.platform.access_management.shared.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseMigrationRunner implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseMigrationRunner.class);

    private final JdbcTemplate jdbcTemplate;

    public DatabaseMigrationRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        cleanupRemovedColumns();
        ensureAuthAuditLogIndexes();
    }

    /**
     * Drop columns that were removed from the JPA model so Hibernate's
     * schema-update path no longer trips over them.
     */
    private void cleanupRemovedColumns() {
        try {
            LOGGER.info("Executing manual schema migrations to clean up removed columns...");
            jdbcTemplate.execute("ALTER TABLE t_invitation_entity DROP COLUMN IF EXISTS email CASCADE;");
            jdbcTemplate.execute("ALTER TABLE t_invitation_entity DROP COLUMN IF EXISTS username CASCADE;");
            jdbcTemplate.execute("ALTER TABLE t_security_audit_log DROP CONSTRAINT IF EXISTS t_security_audit_log_event_type_check;");
            LOGGER.info("Manual schema migrations completed successfully.");
        } catch (Exception e) {
            LOGGER.warn("Could not alter t_invitation_entity. This is normal if the table does not exist yet: {}", e.getMessage());
        }
    }

    /**
     * Create the {@code t_auth_audit_log} indexes idempotently.
     *
     * <p>These indexes used to live as JPA {@code @Index} annotations on
     * {@code AuthAuditLog}, but Hibernate's {@code ddl-auto=update} keeps
     * re-issuing {@code CREATE INDEX} every boot which logs a noisy
     * "relation already exists" stack trace. Owning their lifecycle here
     * with {@code CREATE INDEX IF NOT EXISTS} keeps the production schema
     * identical and silences the boot logs.
     */
    private void ensureAuthAuditLogIndexes() {
        try {
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_audit_timestamp " +
                    "ON t_auth_audit_log (timestamp)");
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_audit_user_timestamp " +
                    "ON t_auth_audit_log (user_id, timestamp)");
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_audit_identifier_event " +
                    "ON t_auth_audit_log (identifier_value, event_type)");
            LOGGER.debug("t_auth_audit_log indexes ensured.");
        } catch (Exception e) {
            LOGGER.warn("Could not ensure t_auth_audit_log indexes (may not exist yet): {}", e.getMessage());
        }
    }
}
