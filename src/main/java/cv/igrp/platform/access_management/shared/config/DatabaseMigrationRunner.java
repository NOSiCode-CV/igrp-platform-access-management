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
        ensureUserSessionSchema();
        ensureUserTokensFloor();
        ensureUserStatusCheckConstraint();
        ensureAuthAuditLogIdentifierTypeConstraint();
    }

    /**
     * Phase F1 — add the {@code tokens_not_valid_before} floor column on
     * {@code t_user}. Idempotent so the runner can ship before Hibernate's
     * {@code ddl-auto=update} pass picks up the new {@link
     * cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity}
     * field, and so it's a no-op on environments where Hibernate already
     * created the column.
     */
    private void ensureUserTokensFloor() {
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE t_user ADD COLUMN IF NOT EXISTS tokens_not_valid_before TIMESTAMPTZ");
            LOGGER.debug("t_user.tokens_not_valid_before ensured.");
        } catch (Exception e) {
            LOGGER.warn("Could not ensure t_user.tokens_not_valid_before (may not exist yet): {}",
                    e.getMessage());
        }
    }

    /**
     * Phase B (session-management) migrations on {@code t_user_session}:
     * <ul>
     *   <li>Drop the legacy single-active-session-per-user unique constraint.</li>
     *   <li>Rename the legacy {@code user_external_id} column to {@code user_id}
     *       and retype it from {@code VARCHAR} to {@code INTEGER} so it lines up
     *       with {@code IGRPUserEntity.id}. Stored values were already integer
     *       strings on this branch (see commit {@code 648d4f4c}).</li>
     *   <li>Replace the legacy active-per-user uniqueness with a per-(user, device)
     *       partial unique index so the same user can hold concurrent sessions
     *       across distinct devices.</li>
     * </ul>
     */
    private void ensureUserSessionSchema() {
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE t_user_session DROP CONSTRAINT IF EXISTS ux_one_active_session_per_user");
            jdbcTemplate.execute(
                    "DROP INDEX IF EXISTS ux_one_active_session_per_user");
            // Rename legacy column if still present.
            jdbcTemplate.execute(
                    "DO $$ BEGIN " +
                    "  IF EXISTS (SELECT 1 FROM information_schema.columns " +
                    "             WHERE table_name='t_user_session' AND column_name='user_external_id') " +
                    "  AND NOT EXISTS (SELECT 1 FROM information_schema.columns " +
                    "                  WHERE table_name='t_user_session' AND column_name='user_id') THEN " +
                    "    ALTER TABLE t_user_session RENAME COLUMN user_external_id TO user_id; " +
                    "  END IF; " +
                    "END $$;");
            // Phase G2 superseded the Phase B "retype to INTEGER" step: t_user.id
            // and t_user_session.user_id are now VARCHAR(36) UUID. Flyway V8 does
            // the actual type migration. Keeping a retype-to-INTEGER block here
            // would crash on every boot post-G2 (cast UUID strings to integer
            // fails). Block intentionally removed.
            jdbcTemplate.execute(
                    "CREATE UNIQUE INDEX IF NOT EXISTS ux_session_user_device_active " +
                    "ON t_user_session (user_id, device_id) " +
                    "WHERE status = 'ACTIVE'");
            LOGGER.debug("t_user_session schema migrations ensured.");
        } catch (Exception e) {
            LOGGER.warn("Could not ensure t_user_session schema (may not exist yet): {}", e.getMessage());
        }
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
    /**
     * Replaces the formerly-Flyway-managed V7 patch: ensure {@code t_user.status}
     * accepts the {@code TEMPORARY} enum value (added in Phase G3) alongside
     * {@code ACTIVE}, {@code INACTIVE}, {@code DELETED}. Hibernate doesn't
     * generate {@code CHECK} constraints out of {@code @Enumerated(EnumType.STRING)}
     * fields, so we own this constraint here. Runs after Hibernate has created
     * {@code t_user}, so it works under both {@code ddl-auto=update} (dev) and
     * {@code validate} (prod, assuming the table was created some other way).
     */
    private void ensureUserStatusCheckConstraint() {
        try {
            jdbcTemplate.execute(
                    "DO $$ DECLARE cname text; BEGIN " +
                    "  SELECT conname INTO cname FROM pg_constraint " +
                    "    WHERE conrelid = 't_user'::regclass AND contype = 'c' " +
                    "      AND pg_get_constraintdef(oid) ILIKE '%status%'; " +
                    "  IF cname IS NOT NULL THEN " +
                    "    EXECUTE format('ALTER TABLE t_user DROP CONSTRAINT %I', cname); " +
                    "  END IF; " +
                    "END $$;");
            jdbcTemplate.execute(
                    "ALTER TABLE t_user " +
                    "  ADD CONSTRAINT t_user_status_check " +
                    "  CHECK (status IN ('TEMPORARY', 'ACTIVE', 'INACTIVE', 'DELETED'))");
            LOGGER.debug("t_user.status CHECK constraint ensured.");
        } catch (Exception e) {
            LOGGER.warn("Could not ensure t_user.status CHECK constraint (table may not exist yet): {}",
                    e.getMessage());
        }
    }

    /**
     * Replaces the formerly-Flyway-managed V2 patch: ensure the
     * {@code identifier_type} CHECK on {@code t_auth_audit_log} accepts the
     * current enum set ({@code CNI}, {@code CMDCV}, {@code EMAIL}, {@code UNKNOWN}).
     * Drops any prior auto-generated or hand-rolled constraint first so the
     * step is idempotent across versions.
     */
    private void ensureAuthAuditLogIdentifierTypeConstraint() {
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE t_auth_audit_log " +
                    "  DROP CONSTRAINT IF EXISTS auth_audit_log_identifier_type_check");
            jdbcTemplate.execute(
                    "ALTER TABLE t_auth_audit_log " +
                    "  DROP CONSTRAINT IF EXISTS t_auth_audit_log_identifier_type_check");
            jdbcTemplate.execute(
                    "ALTER TABLE t_auth_audit_log " +
                    "  ADD CONSTRAINT t_auth_audit_log_identifier_type_check " +
                    "  CHECK (identifier_type IN ('CNI', 'CMDCV', 'EMAIL', 'UNKNOWN'))");
            LOGGER.debug("t_auth_audit_log.identifier_type CHECK constraint ensured.");
        } catch (Exception e) {
            LOGGER.warn("Could not ensure t_auth_audit_log.identifier_type CHECK constraint "
                    + "(table may not exist yet): {}", e.getMessage());
        }
    }

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
