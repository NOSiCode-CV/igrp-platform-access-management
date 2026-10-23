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
        ensureUserSessionSchema();
        ensureUserTokensFloor();
        ensureUserStatusCheckConstraint();
        ensureSpringSessionSchema();
    }

    /**
     * R3.2 — the Spring Session tables backing the shared servlet session.
     *
     * <p>Transcribed from the DDL Spring Session ships at
     * {@code org/springframework/session/jdbc/schema-postgresql.sql}, written
     * {@code IF NOT EXISTS} so it is a no-op on every boot after the first.
     *
     * <p>Owned here rather than by {@code spring.session.jdbc.initialize-schema}
     * for two reasons: this repository already keeps its DDL in one place, and
     * {@code never} removes any question about whether Boot's initializer is
     * idempotent against tables that already exist.
     *
     * <p>Not JPA entities, so Hibernate's {@code ddl-auto} never touches them.
     * Rows are pruned by {@code spring.session.jdbc.cleanup-cron} — which is a
     * different mechanism from {@code SessionCleanupScheduler}, and a different
     * table: {@code SPRING_SESSION} holds the browser-side SSO session,
     * {@code t_user_session} holds iGRP's own per-application sessions.
     *
     * <p>Unlike the other migrations here, a failure is <b>not</b> swallowed. If
     * these tables are missing, every login fails on the first session write,
     * and failing at startup with the cause is better than serving traffic that
     * cannot authenticate.
     *
     * <p>On ordering: a {@link CommandLineRunner} runs after the context has
     * refreshed, so the web server is already listening when this executes. That
     * is safe because Kubernetes gates traffic on the readiness probe, and
     * {@code ApplicationReadyEvent} — which flips readiness to accepting traffic
     * — fires only after every runner has completed. A login cannot arrive
     * before the tables exist. If this service were ever fronted by something
     * that routes on liveness alone, this DDL would need to move into context
     * refresh instead.
     */
    private void ensureSpringSessionSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS SPRING_SESSION (
                    PRIMARY_ID CHAR(36) NOT NULL,
                    SESSION_ID CHAR(36) NOT NULL,
                    CREATION_TIME BIGINT NOT NULL,
                    LAST_ACCESS_TIME BIGINT NOT NULL,
                    MAX_INACTIVE_INTERVAL INT NOT NULL,
                    EXPIRY_TIME BIGINT NOT NULL,
                    PRINCIPAL_NAME VARCHAR(100),
                    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
                )""");
        jdbcTemplate.execute(
                "CREATE UNIQUE INDEX IF NOT EXISTS SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID)");
        jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME)");
        jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME)");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS SPRING_SESSION_ATTRIBUTES (
                    SESSION_PRIMARY_ID CHAR(36) NOT NULL,
                    ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
                    ATTRIBUTE_BYTES BYTEA NOT NULL,
                    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK
                        PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
                    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK
                        FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES SPRING_SESSION(PRIMARY_ID)
                        ON DELETE CASCADE
                )""");
        LOGGER.info("SPRING_SESSION schema ensured (shared servlet session, R3.2).");
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
            // One ACTIVE session per (user, device) is NO LONGER true, so the
            // partial unique index that enforced it must go.
            //
            // Several IGRP applications share one browser, each runs its own
            // authorization_code exchange, and all of them derive the same
            // device_id — it is hashed from (User-Agent, client_ip, client_id)
            // of the SERVER-TO-SERVER token call, so every login through one
            // Next.js app server lands on the same value. The application stopped
            // closing the previous row for that pair, which is correct: closing
            // it signed sibling applications out. But this index still rejected
            // the insert, so the second login failed with a duplicate-key error
            // that surfaced to users as a bare 401 at /oauth2/token.
            //
            // Dropped rather than reinstating the close: the constraint encodes
            // the one-application-per-browser assumption the platform has
            // outgrown. The per-user cap in SessionIssuanceService is what bounds
            // sessions now. ix_session_user_device (non-unique, declared on the
            // entity) still serves the lookups.
            jdbcTemplate.execute(
                    "DROP INDEX IF EXISTS ux_session_user_device_active");
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
}
