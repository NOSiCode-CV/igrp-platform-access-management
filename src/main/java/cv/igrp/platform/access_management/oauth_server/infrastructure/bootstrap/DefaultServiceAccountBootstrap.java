package cv.igrp.platform.access_management.oauth_server.infrastructure.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Seeds the default iGRP Access Management service account on startup, so
 * the infra team can automate downstream client + service-account provisioning
 * from a CLI without pre-populating the DB by hand.
 *
 * <p>Runs on {@link ApplicationReadyEvent} — one phase after
 * {@link DefaultOAuthClientBootstrap}'s {@link org.springframework.boot.CommandLineRunner}
 * — so the linked OAuth client already exists, and the {@code igrp.client.*}
 * and {@code igrp.service_account.*} permission rows have been seeded by
 * whichever mechanism your deployment uses (Flyway migration or an in-process
 * sync). Missing permission rows are skipped with a WARN and the SA is still
 * created; the grants can be re-attempted on the next boot.
 *
 * <p>The manual equivalent this replaces:
 *
 * <pre>
 *   INSERT INTO t_service_account
 *     (id, active, created_at, description, name, updated_at, oauth_client_id)
 *   VALUES (gen_random_uuid(), true, NOW(),
 *           'iGRP Access Management Service Account',
 *           'igrp-access-management-sa',
 *           NOW(),
 *           &lt;UUID of the OAuth client with client_id='igrp-access-management'&gt;);
 *
 *   INSERT INTO t_service_account_permission_grant (granted_at, permission_id, service_account_id)
 *   VALUES (NOW(), &lt;id of igrp.client.list&gt;,          &lt;SA UUID&gt;),
 *          (NOW(), &lt;id of igrp.client.view&gt;,          &lt;SA UUID&gt;),
 *          (NOW(), &lt;id of igrp.client.create&gt;,        &lt;SA UUID&gt;),
 *          (NOW(), &lt;id of igrp.client.update&gt;,        &lt;SA UUID&gt;),
 *          (NOW(), &lt;id of igrp.client.delete&gt;,        &lt;SA UUID&gt;),
 *          (NOW(), &lt;id of igrp.client.manage&gt;,        &lt;SA UUID&gt;),
 *          (NOW(), &lt;id of igrp.service_account.list&gt;, &lt;SA UUID&gt;),
 *          … same for view/create/update/delete/manage;
 * </pre>
 *
 * <p><b>Why by name, not by id.</b> The infra team's script grants
 * "permissions with ids 19-24". Row ids drift with migration order and
 * environment reseeding, so this bootstrap looks permissions up by their
 * canonical {@code name} column. The set granted is fixed in
 * {@link #DEFAULT_PERMISSION_NAMES}: all {@code igrp.client.*} plus all
 * {@code igrp.service_account.*} permissions, giving the SA the minimum
 * privilege needed to provision downstream clients and service accounts
 * for each API/UI service.
 *
 * <p><b>Config surface</b> (all optional):
 * <ul>
 *   <li>{@code igrp.oauth.default-service-account.enabled} (default {@code true}) —
 *       set to {@code false} to skip seeding entirely.</li>
 *   <li>{@code igrp.oauth.default-service-account.name} (default
 *       {@code igrp-access-management-sa}).</li>
 *   <li>{@code igrp.oauth.default-service-account.description} (default
 *       {@code iGRP Access Management Service Account}).</li>
 *   <li>{@code igrp.oauth.default-service-account.client-id} (default
 *       {@code igrp-access-management} — the default OAuth client seeded by
 *       {@link DefaultOAuthClientBootstrap}).</li>
 * </ul>
 *
 * <p>Idempotent: a subsequent boot re-checks and inserts only the permission
 * grants that are missing; the SA row itself is skipped if it already exists.
 */
@Configuration
public class DefaultServiceAccountBootstrap {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultServiceAccountBootstrap.class);

    /**
     * Full permission set granted to the default SA. Kept as a plain list of
     * canonical permission names so a rename (e.g. adding an
     * {@code igrp.client.rotate_secret} in future) is a one-line change here
     * and does NOT require touching migration ids.
     */
    private static final List<String> DEFAULT_PERMISSION_NAMES = List.of(
            // OAuth2 client CRUD
            "igrp.client.list",
            "igrp.client.view",
            "igrp.client.create",
            "igrp.client.update",
            "igrp.client.delete",
            "igrp.client.manage",
            // Service-account CRUD (added alongside this bootstrap)
            "igrp.service_account.list",
            "igrp.service_account.view",
            "igrp.service_account.create",
            "igrp.service_account.update",
            "igrp.service_account.delete",
            "igrp.service_account.manage"
    );

    private final JdbcTemplate jdbcTemplate;
    private final boolean enabled;
    private final String serviceAccountName;
    private final String serviceAccountDescription;
    private final String defaultClientId;

    public DefaultServiceAccountBootstrap(
            JdbcTemplate jdbcTemplate,
            @Value("${igrp.oauth.default-service-account.enabled:true}") boolean enabled,
            @Value("${igrp.oauth.default-service-account.name:igrp-access-management-sa}") String serviceAccountName,
            @Value("${igrp.oauth.default-service-account.description:iGRP Access Management Service Account}") String serviceAccountDescription,
            @Value("${igrp.oauth.default-service-account.client-id:"
                    + DefaultOAuthClientBootstrap.DEFAULT_CLIENT_ID + "}") String defaultClientId) {
        this.jdbcTemplate = jdbcTemplate;
        this.enabled = enabled;
        this.serviceAccountName = serviceAccountName;
        this.serviceAccountDescription = serviceAccountDescription;
        this.defaultClientId = defaultClientId;
    }

    /**
     * Fires after Spring's context is fully initialised. Placed one Order
     * later than the default {@code CommandLineRunner} so
     * {@link DefaultOAuthClientBootstrap} has had a chance to seed the
     * linked OAuth client on the same boot.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(Integer.MIN_VALUE + 100)
    @Transactional
    public void seedDefaultServiceAccount() {
        if (!enabled) {
            LOGGER.info("[Default SA Bootstrap] igrp.oauth.default-service-account.enabled=false — skipping");
            return;
        }

        UUID clientId = findOAuthClientId(defaultClientId);
        if (clientId == null) {
            LOGGER.warn("[Default SA Bootstrap] OAuth client '{}' not found — skipping SA seeding. "
                            + "Set igrp.oauth.default-client.secret so the default client is created, "
                            + "or set igrp.oauth.default-service-account.client-id to an existing client.",
                    defaultClientId);
            return;
        }

        UUID serviceAccountId = findServiceAccountIdByName(serviceAccountName);
        if (serviceAccountId == null) {
            serviceAccountId = createServiceAccount(clientId);
            LOGGER.info("[Default SA Bootstrap] Seeded default service account '{}' (id={}) linked to OAuth client '{}' (id={})",
                    serviceAccountName, serviceAccountId, defaultClientId, clientId);
        } else {
            LOGGER.debug("[Default SA Bootstrap] Default service account '{}' already present (id={})",
                    serviceAccountName, serviceAccountId);
        }

        grantMissingPermissions(serviceAccountId);
    }

    // ─── steps ──────────────────────────────────────────────────────────

    /**
     * Look up an OAuth client's UUID by its {@code client_id} string.
     * Returns {@code null} when no such client exists — bootstrap logs and
     * moves on rather than throwing on a legitimately fresh install where
     * {@code igrp.oauth.default-client.secret} was intentionally left unset.
     */
    private UUID findOAuthClientId(String clientId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM t_oauth_client WHERE client_id = ? LIMIT 1",
                    UUID.class,
                    clientId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private UUID findServiceAccountIdByName(String name) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM t_service_account WHERE name = ? LIMIT 1",
                    UUID.class,
                    name);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private UUID createServiceAccount(UUID oauthClientId) {
        UUID id = UUID.randomUUID();
        // t_service_account has:
        //   id UUID PK, active BOOL, created_at TIMESTAMP, description TEXT,
        //   name VARCHAR, updated_at TIMESTAMP, oauth_client_id UUID FK (UNIQUE — 1:1 pairing),
        //   application_id INTEGER (nullable — SA is app-scoped only when set)
        jdbcTemplate.update(
                """
                INSERT INTO t_service_account
                    (id, active, created_at, description, name, updated_at, oauth_client_id)
                VALUES (?, true, NOW(), ?, ?, NOW(), ?)
                """,
                id,
                serviceAccountDescription,
                serviceAccountName,
                oauthClientId);
        return id;
    }

    /**
     * Grants every permission in {@link #DEFAULT_PERMISSION_NAMES} that
     * isn't already granted to this SA. Missing permission ROWS (looked up
     * by name) are logged at WARN and skipped — they can land on a later
     * boot after Flyway or the runtime sync has caught up.
     */
    private void grantMissingPermissions(UUID serviceAccountId) {
        int granted = 0;
        int alreadyPresent = 0;
        int missingPermission = 0;

        for (String permissionName : DEFAULT_PERMISSION_NAMES) {
            Long permissionId = findPermissionIdByName(permissionName);
            if (permissionId == null) {
                LOGGER.warn("[Default SA Bootstrap] Permission '{}' not found in t_permission — skipping grant. "
                                + "Will retry on next boot.",
                        permissionName);
                missingPermission++;
                continue;
            }
            int inserted = jdbcTemplate.update(
                    """
                    INSERT INTO t_service_account_permission_grant
                        (granted_at, permission_id, service_account_id)
                    SELECT NOW(), ?, ?
                    WHERE NOT EXISTS (
                        SELECT 1 FROM t_service_account_permission_grant
                         WHERE permission_id = ? AND service_account_id = ?
                    )
                    """,
                    permissionId, serviceAccountId,
                    permissionId, serviceAccountId);
            if (inserted > 0) {
                granted++;
            } else {
                alreadyPresent++;
            }
        }

        LOGGER.info("[Default SA Bootstrap] Permission grants for SA '{}': granted={}, alreadyPresent={}, missingPermissionRow={}",
                serviceAccountName, granted, alreadyPresent, missingPermission);
    }

    private Long findPermissionIdByName(String name) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM t_permission WHERE name = ? LIMIT 1",
                    Long.class,
                    name);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
}
