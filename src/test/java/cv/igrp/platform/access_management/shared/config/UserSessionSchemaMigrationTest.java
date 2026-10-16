package cv.igrp.platform.access_management.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * The partial unique index {@code ux_session_user_device_active} must be
 * dropped, never recreated.
 *
 * <p>It enforced one ACTIVE session per (user, device). That stopped being true
 * when several IGRP applications began sharing a browser: each runs its own
 * authorization_code exchange and all of them derive the SAME device_id,
 * because it is hashed from the server-to-server token call. The application
 * correctly stopped closing the previous row — closing it signed siblings out —
 * but this index still rejected the insert, and the duplicate-key error reached
 * users as a bare 401 at /oauth2/token. Production incident, 2026-10-16.
 */
class UserSessionSchemaMigrationTest {

    private List<String> statementsExecuted() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DatabaseMigrationRunner runner = new DatabaseMigrationRunner(jdbcTemplate);

        ReflectionTestUtils.invokeMethod(runner, "ensureUserSessionSchema");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce()).execute(sql.capture());
        return sql.getAllValues();
    }

    @Test
    @DisplayName("the one-session-per-device index is dropped")
    void dropsTheUniqueIndex() {
        assertTrue(statementsExecuted().stream()
                        .anyMatch(s -> s.contains("DROP INDEX IF EXISTS ux_session_user_device_active")),
                "existing deployments already carry this index — the migration must remove it");
    }

    @Test
    @DisplayName("nothing recreates it")
    void neverRecreatesTheUniqueIndex() {
        assertFalse(statementsExecuted().stream()
                        .anyMatch(s -> s.contains("CREATE UNIQUE INDEX")
                                && s.contains("ux_session_user_device_active")),
                "recreating this index breaks login for every user with a second application open");
    }

    @Test
    @DisplayName("no unique index on (user_id, device_id) is created under any name")
    void noUniqueIndexOnUserAndDevice() {
        assertTrue(statementsExecuted().stream()
                        .filter(s -> s.contains("CREATE UNIQUE INDEX"))
                        .noneMatch(s -> s.contains("user_id") && s.contains("device_id")),
                "several ACTIVE sessions per (user, device) are expected now; the per-user cap is the bound");
    }
}
