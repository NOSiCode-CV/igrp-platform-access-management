package cv.igrp.platform.access_management.compat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v3-29 (#37): AM 0.3 starts from a migrated copy of a 0.2 database.
 *
 * <p>The 0.2 database is rebuilt as a 0.2 installation has it: the pre-Flyway
 * schema (Hibernate's tables, captured as {@code base-schema.sql} in
 * {@code e6fab1b5}), the migrations the 0.2 line ships (V6..V16_1, unchanged
 * since), and 0.2 data ({@code compat-0.2/seed-0.2.sql}). AM 0.3's Flyway then
 * migrates it. Each 0.3 ticket ships its own migration (#8); this test checks
 * them together, and that every one is idempotent, on the migrated database and
 * on a fresh install.
 *
 * <p>Its own Postgres, not the compat suite's shared one: the databases here are
 * built and compared from scratch.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MigrationFromV02IT {

    /** The newest migration the 0.2 line ships ({@code deploy/0.2.0-beta}). */
    private static final MigrationVersion LAST_02 = MigrationVersion.fromVersion("16.1");
    /** What {@code production} runs: the 0.2 line up to V15_1, so V16_1 runs with the 0.3 migrations. */
    private static final MigrationVersion PRODUCTION_02 = MigrationVersion.fromVersion("15.1");
    private static final Pattern VERSION = Pattern.compile("V(\\d+(?:_\\d+)?)__.*\\.sql");

    private final PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    /** A 0.2 database at V16_1, migrated. */
    private JdbcTemplate migrated;
    /** A 0.2 database at V15_1 (production), migrated. */
    private JdbcTemplate fromProduction;
    private JdbcTemplate fresh;

    @BeforeAll
    void databases() throws IOException {
        postgres.start();
        JdbcTemplate admin = jdbc("postgres");
        for (String database : List.of("migrated", "production", "stepwise", "fresh")) {
            admin.execute("CREATE DATABASE " + database);
        }
        migrated = database02("migrated", LAST_02);
        flyway("migrated").load().migrate();
        fromProduction = database02("production", PRODUCTION_02);
        flyway("production").load().migrate();

        fresh = jdbc("fresh");
        script(fresh, new ClassPathResource("db/bootstrap/base-schema.sql"));
        flyway("fresh").load().migrate();
    }

    /** A 0.2 installation: the pre-Flyway schema, the 0.2 migrations up to {@code last}, and 0.2 data. */
    private JdbcTemplate database02(String database, MigrationVersion last) throws IOException {
        JdbcTemplate db = jdbc(database);
        script(db, new ClassPathResource("compat-0.2/pre-flyway-schema.sql"));
        flyway(database).target(last).load().migrate();
        script(db, new ClassPathResource("compat-0.2/seed-0.2.sql"));
        return db;
    }

    @AfterAll
    void stop() {
        postgres.stop();
    }

    @Test
    @DisplayName("The 0.2 database migrates to every 0.3 migration")
    void migratesToTheLatest() {
        assertThat(migrated.queryForList("""
                SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank""", String.class))
                .containsSubsequence("16.1", "17.1", "18.1", "19.1", "20.1", "21.1", "22.1", "23.1", "24.1", "25.1",
                        "26.1", "27.1", "28.1", "29.1", "30.1");
        assertThat(migrated.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE NOT success",
                Integer.class)).isZero();
    }

    @Test
    @DisplayName("The new columns and tables exist")
    void newColumnsAndTables() {
        assertThat(columns(migrated, "t_permission")).contains("permission_key", "service", "application_id",
                "last_seen_version");
        assertThat(columns(migrated, "t_role_permission")).contains("scope");
        assertThat(columns(migrated, "revinfo")).contains("username", "trace_id");
        assertThat(columns(migrated, "t_oauth_client")).contains("idp");
        assertThat(tables(migrated)).contains("t_registered_service", "t_user_application_role",
                "t_permission_pending_deletion", "t_application_page", "t_application_page_permission");
    }

    @Test
    @DisplayName("iGRP's session tables are dropped (#33): on the migrated 0.2 databases and on a fresh install")
    void sessionTablesDropped() {
        for (JdbcTemplate db : List.of(migrated, fromProduction, fresh)) {
            assertThat(tables(db)).doesNotContain("t_user_session", "t_user_session_aud");
            assertThat(db.queryForObject("""
                    SELECT count(*) FROM information_schema.sequences WHERE sequence_name = 't_user_session_id_seq'""",
                    Integer.class)).isZero();
        }
        // The browser's SSO session is a different mechanism and stays.
        assertThat(tables(fresh)).contains("spring_session", "spring_session_attributes");
    }

    @Test
    @DisplayName("Scopes are assigned: all for root's role, department for every other")
    void scopesAssigned() {
        assertThat(migrated.queryForList("""
                SELECT r.code || '=' || rp.scope FROM t_role_permission rp JOIN t_role r ON r.id = rp.role_id
                 WHERE rp.permission IN (900, 906)""", String.class))
                .containsExactlyInAnyOrder("DEPT_IGRP.superadmin=all", "RH_NOTIFY=department");
        assertThat(migrated.queryForObject("SELECT count(*) FROM t_role_permission WHERE scope IS NULL",
                Integer.class)).isZero();
        // V25_1 keeps the scope V23_1 gave: root's igrp.users.view becomes users.read with all.
        assertThat(migrated.queryForList("""
                SELECT rp.scope FROM t_role_permission rp JOIN t_role r ON r.id = rp.role_id
                  JOIN t_permission p ON p.id = rp.permission
                 WHERE r.code = 'DEPT_IGRP.superadmin' AND p.permission_key = 'access-management-api.users.read'""",
                String.class)).containsExactly("all");
    }

    @Test
    @DisplayName("AM's igrp.* assignments are mapped to route keys; other services' permissions are left alone")
    void amPermissionsMapped() {
        List<String> adminKeys = migrated.queryForList("""
                SELECT p.permission_key FROM t_role_permission rp JOIN t_role r ON r.id = rp.role_id
                  JOIN t_permission p ON p.id = rp.permission WHERE r.code = 'RH_ADMIN'""", String.class);
        assertThat(adminKeys).contains("access-management-api.users.read", "access-management-api.users.list",
                "access-management-api.departments.read")
                .allMatch(key -> key != null && key.startsWith("access-management-api."))
                // /api/m2m/** was for service accounts only.
                .doesNotContain("access-management-api.m2m.users.create");
        assertThat(migrated.queryForList("""
                SELECT p.permission_key FROM t_service_account_permission_grant g JOIN t_permission p ON p.id = g.permission_id
                 WHERE g.service_account_id = '00000000-0000-0000-0000-00000000a900'""", String.class))
                .contains("access-management-api.routes.register.create", "access-management-api.m2m.users.create");
        assertThat(migrated.queryForList("SELECT name || '=' || status FROM t_permission WHERE id BETWEEN 901 AND 907",
                String.class)).containsExactlyInAnyOrder("igrp.users.view=DELETED", "igrp.users.list=DELETED",
                "igrp.departments.view=DELETED", "igrp.m2m.sync=DELETED", "igrp.users.pii.view=DELETED",
                "igrp.notification.send=ACTIVE", "igrp.applications.view=DELETED");
        assertThat(migrated.queryForObject("SELECT count(*) FROM t_role_permission WHERE permission = 906",
                Integer.class)).isOne();
        // Two old permissions mapping to one key (users.view, applications.view -> files.url.list): one grant.
        assertThat(migrated.queryForObject("""
                SELECT count(*) FROM t_role_permission rp JOIN t_role r ON r.id = rp.role_id
                  JOIN t_permission p ON p.id = rp.permission
                 WHERE r.code = 'RH_ADMIN' AND p.permission_key = 'access-management-api.files.url.list'""",
                Integer.class)).isOne();
        // Department links move too.
        assertThat(migrated.queryForList("""
                SELECT d.code FROM t_permission_department pd JOIN t_department d ON d.id = pd.department
                  JOIN t_permission p ON p.id = pd.permission_id
                 WHERE p.permission_key = 'access-management-api.users.read'""",
                String.class)).containsExactly("DEPT_RH");
        // V28_1: who reads the security audit reads the business audit too (#32).
        assertThat(adminKeys).contains("access-management-api.auth.audit.list");
        assertThat(migrated.queryForList("""
                SELECT p.permission_key FROM t_role_permission rp JOIN t_role r ON r.id = rp.role_id
                  JOIN t_permission p ON p.id = rp.permission
                 WHERE r.code = 'RH_ADMIN' AND p.permission_key LIKE 'access-management-api.auth.audit.business.%'""",
                String.class)).containsExactlyInAnyOrder("access-management-api.auth.audit.business.list",
                "access-management-api.auth.audit.business.validate.list");
        assertThat(migrated.queryForList("""
                SELECT d.code FROM t_permission_department pd JOIN t_department d ON d.id = pd.department
                  JOIN t_permission p ON p.id = pd.permission_id
                 WHERE p.permission_key = 'access-management-api.auth.audit.business.list'""",
                String.class)).containsExactly("DEPT_RH");
    }

    @Test
    @DisplayName("The audit tables are append-only: their triggers and grants are in place")
    void auditGrants() {
        assertThat(migrated.queryForList("""
                SELECT tgname FROM pg_trigger WHERE NOT tgisinternal
                   AND tgrelid IN ('t_security_audit_log'::regclass, 't_security_audit_archive'::regclass)""",
                String.class))
                .contains("no_update_delete_security_audit", "no_update_delete_security_audit_archive");
        // The grants REVOKE leaves (a superuser, as in this container, bypasses them, but they are set).
        assertThat(privileges("t_security_audit_log")).isNotEmpty().doesNotContain("UPDATE");
        assertThat(privileges("t_security_audit_archive")).isNotEmpty().doesNotContain("UPDATE", "DELETE");
        assertThat(migrated.queryForList("""
                SELECT tgname FROM pg_trigger WHERE NOT tgisinternal
                   AND tgrelid = 't_business_audit_event'::regclass""", String.class))
                .contains("no_update_delete_business_audit", "no_truncate_business_audit");
        assertThat(privileges("t_business_audit_event")).isNotEmpty().doesNotContain("UPDATE", "DELETE", "TRUNCATE");
    }

    @Test
    @DisplayName("Users, departments, roles and applications survive the migration")
    void dataSurvives() {
        assertThat(migrated.queryForObject("SELECT count(*) FROM t_user WHERE id LIKE '00000000-0000-0000-0000-00000000090%'",
                Integer.class)).isEqualTo(2);
        assertThat(migrated.queryForList("SELECT code FROM t_department WHERE id >= 900", String.class))
                .containsExactlyInAnyOrder("DEPT_IGRP", "DEPT_RH");
        assertThat(migrated.queryForObject("SELECT count(*) FROM t_user_role_assignment", Integer.class)).isEqualTo(3);
    }

    @Test
    @DisplayName("A migrated 0.2 database, from V16_1 or production's V15_1, has the same schema as a fresh install")
    void sameSchemaAsFreshInstall() {
        assertThat(schema(migrated)).isEqualTo(schema(fresh));
        assertThat(schema(fromProduction)).isEqualTo(schema(fresh));
    }

    @Test
    @DisplayName("Production's V15_1 database migrates to the same grants")
    void productionMigratesAlike() {
        String grants = """
                SELECT r.code || ' ' || p.permission_key || ' ' || rp.scope FROM t_role_permission rp
                  JOIN t_role r ON r.id = rp.role_id JOIN t_permission p ON p.id = rp.permission
                 WHERE p.permission_key IS NOT NULL ORDER BY 1""";
        assertThat(fromProduction.queryForList(grants, String.class))
                .isNotEmpty()
                .isEqualTo(migrated.queryForList(grants, String.class));
    }

    @Test
    @DisplayName("Each 0.3 migration, run again right after it is applied to 0.2 data, changes nothing")
    void eachMigrationIsIdempotentWhenItRuns() throws IOException {
        JdbcTemplate db = database02("stepwise", LAST_02);
        for (Resource migration : migrations03()) {
            flyway("stepwise").target(version(migration)).load().migrate();
            String before = snapshot(db);
            String sql = migration.getContentAsString(StandardCharsets.UTF_8);
            transactions(db).executeWithoutResult(status -> db.execute(sql));
            assertThat(snapshot(db)).as(migration.getFilename() + " run again right after it ran").isEqualTo(before);
        }
    }

    @Test
    @DisplayName("Every 0.3 migration is idempotent on the migrated database and on a fresh install")
    void everyMigrationIsIdempotent() throws IOException {
        for (Resource migration : migrations03()) {
            String sql = migration.getContentAsString(StandardCharsets.UTF_8);
            for (JdbcTemplate database : List.of(migrated, fromProduction, fresh)) {
                String before = snapshot(database);
                transactions(database).executeWithoutResult(status -> database.execute(sql));
                assertThat(snapshot(database)).as(migration.getFilename() + " run again").isEqualTo(before);
            }
        }
    }

    /** The privileges the table's ACL grants; empty when it still has the default ACL. */
    private List<String> privileges(String table) {
        return migrated.queryForList("""
                SELECT a.privilege_type FROM pg_class c, aclexplode(c.relacl) a
                 WHERE c.oid = ?::regclass AND a.grantee = (SELECT oid FROM pg_roles WHERE rolname = current_user)""",
                String.class, table);
    }

    /** The 0.3 migrations: every one after the 0.2 line's last. */
    private static List<Resource> migrations03() throws IOException {
        List<Resource> result = new ArrayList<>();
        for (Resource resource : new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/V*.sql")) {
            Matcher matcher = VERSION.matcher(resource.getFilename());
            if (matcher.matches() && MigrationVersion.fromVersion(matcher.group(1).replace('_', '.'))
                    .isNewerThan(LAST_02.getVersion())) {
                result.add(resource);
            }
        }
        result.sort(Comparator.comparing(MigrationFromV02IT::version));
        assertThat(result).hasSizeGreaterThanOrEqualTo(10);
        return result;
    }

    private static MigrationVersion version(Resource migration) {
        return MigrationVersion.fromVersion(
                VERSION.matcher(migration.getFilename()).replaceAll("$1").replace('_', '.'));
    }

    /**
     * Tables, columns (type, nullability, default), constraints, indexes, triggers and functions (their
     * definitions) and the owner's table grants, sorted; no data.
     */
    private static List<String> schema(JdbcTemplate db) {
        List<String> lines = new ArrayList<>(db.queryForList("""
                SELECT 'column ' || table_name || '.' || column_name || ' ' || data_type
                       || coalesce('(' || character_maximum_length || ')', '') || ' ' || is_nullable
                       || ' default ' || coalesce(regexp_replace(column_default, 'nextval\\(''[^'']*''::regclass\\)', 'nextval'), '-')
                  FROM information_schema.columns WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'""",
                String.class));
        lines.addAll(db.queryForList("""
                SELECT 'constraint ' || conrelid::regclass || ' ' || contype::text || ' ' || pg_get_constraintdef(oid)
                  FROM pg_constraint WHERE connamespace = 'public'::regnamespace""", String.class));
        lines.addAll(db.queryForList("""
                SELECT 'index ' || tablename || ' ' || regexp_replace(indexdef, 'INDEX \\S+ ON', 'INDEX ON')
                  FROM pg_indexes WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'""", String.class));
        lines.addAll(db.queryForList("""
                SELECT 'trigger ' || pg_get_triggerdef(oid) FROM pg_trigger
                 WHERE NOT tgisinternal AND tgrelid::regclass::text NOT LIKE 'flyway%'""", String.class));
        lines.addAll(db.queryForList("""
                SELECT 'function ' || pg_get_functiondef(p.oid) FROM pg_proc p
                 WHERE p.pronamespace = 'public'::regnamespace AND p.prokind = 'f'""", String.class));
        lines.addAll(db.queryForList("""
                SELECT 'grants ' || c.relname || ' ' || coalesce(
                       (SELECT string_agg(a.privilege_type, ',' ORDER BY a.privilege_type)
                          FROM aclexplode(c.relacl) a WHERE a.grantee = c.relowner), 'default')
                  FROM pg_class c WHERE c.relnamespace = 'public'::regnamespace AND c.relkind = 'r'
                   AND c.relname <> 'flyway_schema_history'""", String.class));
        lines.sort(String::compareTo);
        return lines;
    }

    /** The schema, every sequence's position and every table's rows (as text, sorted), so any change shows. */
    private static String snapshot(JdbcTemplate db) {
        StringBuilder out = new StringBuilder(String.join("\n", schema(db)));
        out.append('\n').append(db.queryForList("""
                SELECT sequencename || '=' || coalesce(last_value::text, '-') FROM pg_sequences
                 WHERE schemaname = 'public' AND sequencename NOT LIKE 'flyway%' ORDER BY 1""", String.class));
        for (String table : tables(db)) {
            List<Map<String, Object>> rows = db.queryForList(
                    "SELECT md5(string_agg(t::text, '|' ORDER BY t::text)) AS h, count(*) AS n FROM " + table + " t");
            out.append('\n').append(table).append(' ').append(rows.getFirst());
        }
        return out.toString();
    }

    private static List<String> tables(JdbcTemplate db) {
        return db.queryForList("""
                SELECT table_name FROM information_schema.tables
                 WHERE table_schema = 'public' AND table_type = 'BASE TABLE' AND table_name <> 'flyway_schema_history'
                 ORDER BY 1""", String.class);
    }

    private static List<String> columns(JdbcTemplate db, String table) {
        return db.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name = ?",
                String.class, table);
    }

    private static void script(JdbcTemplate db, Resource resource) throws IOException {
        String sql = resource.getContentAsString(StandardCharsets.UTF_8);
        transactions(db).executeWithoutResult(status -> db.execute(sql));
    }

    private static TransactionTemplate transactions(JdbcTemplate db) {
        return new TransactionTemplate(new DataSourceTransactionManager(db.getDataSource()));
    }

    private FluentConfiguration flyway(String database) {
        return Flyway.configure().dataSource(url(database), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").baselineOnMigrate(true).baselineVersion("0");
    }

    private JdbcTemplate jdbc(String database) {
        return new JdbcTemplate(new DriverManagerDataSource(url(database), postgres.getUsername(), postgres.getPassword()));
    }

    private String url(String database) {
        return postgres.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + database + "$1");
    }
}
