package cv.igrp.platform.access_management.shared.config;

import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Lets the service start on a fresh, empty database.
 *
 * <p>The versioned Flyway migrations (V6 onwards) were written for databases
 * that predate Flyway, whose tables Hibernate and {@link DatabaseMigrationRunner}
 * had already created. On an empty database Flyway runs before either of them
 * and V9 fails with {@code relation "t_user_session" does not exist}, so a new
 * environment could not be installed.
 *
 * <p>Before the first migration this callback checks for {@code t_user}. If it
 * is missing, it applies {@code db/bootstrap/base-schema.sql}: a schema-only
 * dump of exactly the pre-Flyway state the migrations are known to apply on.
 * Flyway then runs every migration on top, as on any other database. On an
 * existing database the check finds {@code t_user} and nothing happens.
 *
 * <p>Registered with Flyway as a {@link Callback} bean, which Spring Boot's
 * Flyway auto-configuration picks up.
 */
@Component
public class FreshDatabaseSchemaCallback implements Callback {

    private static final Logger LOGGER = LoggerFactory.getLogger(FreshDatabaseSchemaCallback.class);

    static final String BASE_SCHEMA = "db/bootstrap/base-schema.sql";
    private static final String MARKER_TABLE = "t_user";

    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.BEFORE_MIGRATE;
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return true;
    }

    @Override
    public void handle(Event event, Context context) {
        Connection connection = context.getConnection();
        try {
            if (markerTableExists(connection)) {
                return;
            }
            LOGGER.info("[Fresh database] No {} table: applying {} before the first migration.",
                    MARKER_TABLE, BASE_SCHEMA);
            ScriptUtils.executeSqlScript(connection,
                    new EncodedResource(new ClassPathResource(BASE_SCHEMA), StandardCharsets.UTF_8));
            LOGGER.info("[Fresh database] Base schema applied; Flyway migrations follow.");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not prepare the base schema for a fresh database", e);
        }
    }

    @Override
    public String getCallbackName() {
        return "freshDatabaseSchema";
    }

    private static boolean markerTableExists(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select 1 from information_schema.tables where table_schema = current_schema() and table_name = ?")) {
            statement.setString(1, MARKER_TABLE);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }
}
