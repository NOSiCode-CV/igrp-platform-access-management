package cv.igrp.platform.access_management.oauth_server.infrastructure.bootstrap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultServiceAccountBootstrapTest {

    private static final String DEFAULT_CLIENT_ID = "igrp-access-management";
    private static final String DEFAULT_SA_NAME = "igrp-access-management-sa";
    private static final String DEFAULT_SA_DESCRIPTION = "iGRP Access Management Service Account";

    private JdbcTemplate jdbcTemplate;
    private DefaultServiceAccountBootstrap bootstrap;

    private final UUID clientUuid = UUID.fromString("736947fe-c54d-40db-a696-f8d0a6b58d8a");

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        bootstrap = newBootstrap(true);
    }

    private DefaultServiceAccountBootstrap newBootstrap(boolean enabled) {
        return new DefaultServiceAccountBootstrap(
                jdbcTemplate,
                enabled,
                DEFAULT_SA_NAME,
                DEFAULT_SA_DESCRIPTION,
                DEFAULT_CLIENT_ID);
    }

    // ─── enable flag ────────────────────────────────────────────────────

    @Test
    @DisplayName("enabled=false — skips everything, no DB reads")
    void disabled_skipsEverything() {
        bootstrap = newBootstrap(false);

        bootstrap.seedDefaultServiceAccount();

        verify(jdbcTemplate, never()).queryForObject(any(String.class), any(Class.class), any(Object.class));
        verify(jdbcTemplate, never()).update(any(String.class), any(Object.class));
    }

    // ─── missing OAuth client short-circuits ────────────────────────────

    @Test
    @DisplayName("OAuth client not present — logs and returns without creating SA")
    void oauthClientMissing_skipsSeeding() {
        // client lookup returns empty
        when(jdbcTemplate.queryForObject(
                startsWith("SELECT id FROM t_oauth_client"),
                eq(UUID.class),
                eq(DEFAULT_CLIENT_ID)))
                .thenThrow(new EmptyResultDataAccessException(1));

        bootstrap.seedDefaultServiceAccount();

        // never queried the SA table, never inserted anything
        verify(jdbcTemplate, never()).queryForObject(
                startsWith("SELECT id FROM t_service_account"),
                eq(UUID.class),
                any(Object.class));
        verify(jdbcTemplate, never()).update(startsWith("INSERT INTO t_service_account"), any(Object[].class));
    }

    // ─── fresh install: create SA + grant all resolvable permissions ────

    @Test
    @DisplayName("fresh install: creates SA and grants all 12 permissions")
    void freshInstall_createsSaAndGrantsAllPermissions() {
        // OAuth client resolves
        when(jdbcTemplate.queryForObject(
                startsWith("SELECT id FROM t_oauth_client"),
                eq(UUID.class),
                eq(DEFAULT_CLIENT_ID)))
                .thenReturn(clientUuid);

        // SA lookup returns empty (fresh)
        when(jdbcTemplate.queryForObject(
                startsWith("SELECT id FROM t_service_account"),
                eq(UUID.class),
                eq(DEFAULT_SA_NAME)))
                .thenThrow(new EmptyResultDataAccessException(1));

        // permission lookups: 6 client.* start at 19, 6 service_account.* start at 25
        stubPermissionId("igrp.client.list",              19L);
        stubPermissionId("igrp.client.view",              20L);
        stubPermissionId("igrp.client.create",            21L);
        stubPermissionId("igrp.client.update",            22L);
        stubPermissionId("igrp.client.delete",            23L);
        stubPermissionId("igrp.client.manage",            24L);
        stubPermissionId("igrp.service_account.list",     25L);
        stubPermissionId("igrp.service_account.view",     26L);
        stubPermissionId("igrp.service_account.create",   27L);
        stubPermissionId("igrp.service_account.update",   28L);
        stubPermissionId("igrp.service_account.delete",   29L);
        stubPermissionId("igrp.service_account.manage",   30L);

        // grants return 1 (inserted) each time
        when(jdbcTemplate.update(
                startsWith("INSERT INTO t_service_account_permission_grant"),
                any(Object.class), any(Object.class), any(Object.class), any(Object.class)))
                .thenReturn(1);

        bootstrap.seedDefaultServiceAccount();

        // Verify SA insert fired once with the client uuid
        verify(jdbcTemplate, times(1)).update(
                startsWith("INSERT INTO t_service_account\n"),
                any(UUID.class),                      // generated SA id
                eq(DEFAULT_SA_DESCRIPTION),
                eq(DEFAULT_SA_NAME),
                eq(clientUuid));

        // Verify grants fired 12 times (once per permission)
        verify(jdbcTemplate, times(12)).update(
                startsWith("INSERT INTO t_service_account_permission_grant"),
                any(Object.class), any(Object.class), any(Object.class), any(Object.class));
    }

    // ─── idempotency: SA already exists, only fills missing grants ──────

    @Test
    @DisplayName("SA already exists — doesn't re-insert row, still fills grants")
    void saAlreadyExists_skipsRowInsert_stillFillsGrants() {
        UUID existingSa = UUID.randomUUID();

        when(jdbcTemplate.queryForObject(
                startsWith("SELECT id FROM t_oauth_client"),
                eq(UUID.class),
                eq(DEFAULT_CLIENT_ID)))
                .thenReturn(clientUuid);
        when(jdbcTemplate.queryForObject(
                startsWith("SELECT id FROM t_service_account"),
                eq(UUID.class),
                eq(DEFAULT_SA_NAME)))
                .thenReturn(existingSa);

        // Stub all 12 permissions resolvable
        for (String name : allDefaultPermissionNames()) {
            stubPermissionId(name, (long) (name.hashCode() & 0xFFFF));
        }

        // Every grant conditional INSERT reports 0 rows affected (already present)
        when(jdbcTemplate.update(
                startsWith("INSERT INTO t_service_account_permission_grant"),
                any(Object.class), any(Object.class), any(Object.class), any(Object.class)))
                .thenReturn(0);

        bootstrap.seedDefaultServiceAccount();

        // Row insert MUST NOT fire
        verify(jdbcTemplate, never()).update(
                startsWith("INSERT INTO t_service_account\n"),
                any(UUID.class), any(String.class), any(String.class), any(UUID.class));

        // Grants still attempted 12 times (idempotent no-ops)
        verify(jdbcTemplate, times(12)).update(
                startsWith("INSERT INTO t_service_account_permission_grant"),
                any(Object.class), any(Object.class), any(Object.class), any(Object.class));
    }

    // ─── regression: SA renamed via API but still linked to our client ──

    @Test
    @DisplayName("SA was renamed but the oauth_client_id link still points to our client — reuse it, DO NOT re-insert")
    void saRenamed_reusedByClientId_noDuplicateInsert() {
        // Reproduces the DuplicateKeyException on idx_service_account_oauth_client that
        // happened when the default SA was renamed after first boot: the by-name lookup
        // missed, the bootstrap tried to INSERT another SA against the same client_id
        // (UNIQUE), and Postgres rejected it.
        UUID existingSa = UUID.randomUUID();

        when(jdbcTemplate.queryForObject(
                startsWith("SELECT id FROM t_oauth_client"),
                eq(UUID.class),
                eq(DEFAULT_CLIENT_ID)))
                .thenReturn(clientUuid);

        // by-name lookup returns empty (SA was renamed via the API)
        when(jdbcTemplate.queryForObject(
                startsWith("SELECT id FROM t_service_account"),
                eq(UUID.class),
                eq(DEFAULT_SA_NAME)))
                .thenThrow(new EmptyResultDataAccessException(1));

        // by-oauth_client_id lookup resolves the existing (renamed) SA
        when(jdbcTemplate.queryForObject(
                startsWith("SELECT id FROM t_service_account"),
                eq(UUID.class),
                eq(clientUuid)))
                .thenReturn(existingSa);

        for (String name : allDefaultPermissionNames()) {
            stubPermissionId(name, (long) (name.hashCode() & 0xFFFF));
        }
        when(jdbcTemplate.update(
                startsWith("INSERT INTO t_service_account_permission_grant"),
                any(Object.class), any(Object.class), any(Object.class), any(Object.class)))
                .thenReturn(0);

        bootstrap.seedDefaultServiceAccount();

        // The row INSERT must NOT fire — that is the bug this test guards against.
        verify(jdbcTemplate, never()).update(
                startsWith("INSERT INTO t_service_account\n"),
                any(UUID.class), any(String.class), any(String.class), any(UUID.class));

        // Grants still reconciled on the existing SA (idempotent no-ops here).
        verify(jdbcTemplate, times(12)).update(
                startsWith("INSERT INTO t_service_account_permission_grant"),
                any(Object.class), any(Object.class), any(Object.class), any(Object.class));
    }

    // ─── permission row missing: skipped with warning, not a hard fail ──

    @Test
    @DisplayName("some permissions not yet in t_permission — those grants are skipped, rest complete")
    void missingPermissionRows_areSkipped_othersComplete() {
        when(jdbcTemplate.queryForObject(
                startsWith("SELECT id FROM t_oauth_client"),
                eq(UUID.class),
                eq(DEFAULT_CLIENT_ID)))
                .thenReturn(clientUuid);
        when(jdbcTemplate.queryForObject(
                startsWith("SELECT id FROM t_service_account"),
                eq(UUID.class),
                eq(DEFAULT_SA_NAME)))
                .thenThrow(new EmptyResultDataAccessException(1));

        // Only 6 igrp.client.* permissions exist; igrp.service_account.* aren't seeded yet.
        stubPermissionId("igrp.client.list",   19L);
        stubPermissionId("igrp.client.view",   20L);
        stubPermissionId("igrp.client.create", 21L);
        stubPermissionId("igrp.client.update", 22L);
        stubPermissionId("igrp.client.delete", 23L);
        stubPermissionId("igrp.client.manage", 24L);
        stubPermissionMissing("igrp.service_account.list");
        stubPermissionMissing("igrp.service_account.view");
        stubPermissionMissing("igrp.service_account.create");
        stubPermissionMissing("igrp.service_account.update");
        stubPermissionMissing("igrp.service_account.delete");
        stubPermissionMissing("igrp.service_account.manage");

        when(jdbcTemplate.update(
                startsWith("INSERT INTO t_service_account_permission_grant"),
                any(Object.class), any(Object.class), any(Object.class), any(Object.class)))
                .thenReturn(1);

        bootstrap.seedDefaultServiceAccount();

        // SA row was created
        verify(jdbcTemplate, times(1)).update(
                startsWith("INSERT INTO t_service_account\n"),
                any(UUID.class), eq(DEFAULT_SA_DESCRIPTION), eq(DEFAULT_SA_NAME), eq(clientUuid));

        // Only 6 grants fired (client.*), the 6 service_account.* were skipped
        verify(jdbcTemplate, times(6)).update(
                startsWith("INSERT INTO t_service_account_permission_grant"),
                any(Object.class), any(Object.class), any(Object.class), any(Object.class));
    }

    // ─── helpers ────────────────────────────────────────────────────────

    private void stubPermissionId(String name, long id) {
        when(jdbcTemplate.queryForObject(
                startsWith("SELECT id FROM t_permission"),
                eq(Long.class),
                eq(name)))
                .thenReturn(id);
    }

    private void stubPermissionMissing(String name) {
        when(jdbcTemplate.queryForObject(
                startsWith("SELECT id FROM t_permission"),
                eq(Long.class),
                eq(name)))
                .thenThrow(new EmptyResultDataAccessException(1));
    }

    private static java.util.List<String> allDefaultPermissionNames() {
        return java.util.List.of(
                "igrp.client.list", "igrp.client.view", "igrp.client.create",
                "igrp.client.update", "igrp.client.delete", "igrp.client.manage",
                "igrp.service_account.list", "igrp.service_account.view", "igrp.service_account.create",
                "igrp.service_account.update", "igrp.service_account.delete", "igrp.service_account.manage");
    }
}
