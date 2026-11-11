package cv.igrp.platform.access_management.compat;

import cv.igrp.platform.access_management.shared.config.FreshDatabaseSchemaCallback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A new environment installs on an empty database: the suite's Postgres starts
 * empty, so the application context these tests share was itself installed by
 * {@link FreshDatabaseSchemaCallback} plus every Flyway migration, and passed
 * Hibernate's {@code validate}.
 */
class FreshDatabaseInstallIT extends AbstractCompatIT {

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    DataSource dataSource;
    @Autowired
    FreshDatabaseSchemaCallback callback;

    @Test
    @DisplayName("Every Flyway migration ran successfully on the fresh database")
    void allMigrationsApplied() {
        List<String> applied = jdbc.queryForList(
                "select version from flyway_schema_history where success and version is not null order by installed_rank",
                String.class);

        assertEquals(List.of("6", "7", "8", "9", "10.1", "11.1", "12.1", "13.1", "14.1", "15.1", "16.1", "17.1", "18.1", "19.1", "20.1"), applied);
    }

    @Test
    @DisplayName("On an existing database the callback does nothing")
    void noOpOnExistingDatabase() throws Exception {
        Integer before = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = current_schema()", Integer.class);

        try (Connection connection = dataSource.getConnection()) {
            Context context = mock(Context.class);
            when(context.getConnection()).thenReturn(connection);
            assertTrue(callback.supports(Event.BEFORE_MIGRATE, context));
            // Would fail with "relation already exists" if it tried to apply the base schema.
            callback.handle(Event.BEFORE_MIGRATE, context);
        }

        Integer after = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = current_schema()", Integer.class);
        assertEquals(before, after);
    }
}
