package cv.igrp.platform.access_management.routes.domain.service;

import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.OAuthClientEntity;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.OAuthClientJpaRepository;
import cv.igrp.platform.access_management.routes.application.dto.RouteRegistrationRequestDTO;
import cv.igrp.platform.access_management.routes.application.dto.RouteRegistrationRequestDTO.RegisteredRouteDTO;
import cv.igrp.platform.access_management.routes.application.dto.RouteRegistrationResultDTO;
import cv.igrp.platform.access_management.routes.infrastructure.persistence.RegisteredServiceEntity;
import cv.igrp.platform.access_management.routes.infrastructure.persistence.RegisteredServiceRepository;
import cv.igrp.platform.access_management.routes.config.RouteRegistrationProperties;
import cv.igrp.platform.access_management.security_audit.domain.events.PermissionKeyRenamedEvent;
import cv.igrp.platform.access_management.security_audit.domain.events.PermissionRegisteredEvent;
import cv.igrp.platform.access_management.session.domain.event.RolePermissionChangedEvent;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.events.EventPublisher;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import cv.igrp.platform.access_management.shared.security.ServiceAccountTokenClaims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns a service's routes into permissions (v3-02, spec "Route registration
 * contract"): keys not seen before are created with no assignments; existing
 * keys keep their name, description and assignments.
 *
 * <p>Version gate (v3-03, #11). Versions compare in semver order. A
 * registration from the newest version seen schedules the service's keys it no
 * longer declares for deletion after {@code igrp.routes.deletion-grace};
 * {@link PendingKeyDeletions} deletes them when due. Any registration, of any
 * version, creates the keys it is missing (a hotfix on an older line) and
 * cancels the pending deletion of the keys it declares (a rollback, or an old
 * pod restarting mid-deploy), and nothing else: only the newest schedules.
 * A deleted key declared again comes back with no assignments.
 *
 * <p>Rename (#11): a registration from a client that already registers another
 * service, under a new name, renames that service: its keys are rewritten with
 * the new prefix, keeping every assignment.
 *
 * <p>Ownership: the first registration of a service binds it to the calling
 * client, and only that client may register it again. Keys belong to the
 * calling client's application; a client without one is refused, because its
 * keys could never be granted.
 *
 * <p>Replicas of a service register at the same moment on a deploy: a
 * transaction-scoped advisory lock per client serialises them, so each key is
 * created once and the counts are right. A client registers one service, so
 * the lock also covers that service under its old and new names during a
 * rename. The unique index on the key is the backstop.
 */
@Service
public class RouteRegistrationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RouteRegistrationService.class);

    /** First half of the two-int advisory lock key, distinct from the audit chain's lock. */
    private static final int LOCK_CLASS = 0x524F5554; // "ROUT"

    private final PermissionEntityRepository permissions;
    private final RegisteredServiceRepository services;
    private final OAuthClientJpaRepository clients;
    private final AuthenticationHelper authenticationHelper;
    private final EventPublisher events;
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final RouteRegistrationProperties properties;
    private final PermissionHolders holders;

    /** The newest version's undeclared live keys, scheduled once (an earlier due date stays). */
    private static final String SCHEDULE = """
            INSERT INTO t_permission_pending_deletion (permission_id, service, version, due_at)
            SELECT p.id, :service, :version, now() + make_interval(secs => :grace)
              FROM t_permission p
             WHERE p.service = :service AND p.status <> 'DELETED' AND p.permission_key IS NOT NULL
               AND p.permission_key NOT IN (:keys)
            ON CONFLICT (permission_id) DO NOTHING""";

    private static final String CANCEL = """
            DELETE FROM t_permission_pending_deletion
             WHERE permission_id IN (SELECT id FROM t_permission WHERE permission_key IN (:keys))""";

    private static final String PENDING_COUNT =
            "SELECT count(*) FROM t_permission_pending_deletion WHERE service = ?";

    private static final String RENAME_KEY = """
            UPDATE t_permission
               SET permission_key = ?, service = ?,
                   name = CASE WHEN name = permission_key THEN ? ELSE name END,
                   last_modified_by = 'system', last_modified_date = now()
             WHERE id = ?""";

    public RouteRegistrationService(PermissionEntityRepository permissions,
                                    RegisteredServiceRepository services,
                                    OAuthClientJpaRepository clients,
                                    AuthenticationHelper authenticationHelper,
                                    EventPublisher events,
                                    JdbcTemplate jdbc,
                                    RouteRegistrationProperties properties,
                                    PermissionHolders holders) {
        this.permissions = permissions;
        this.services = services;
        this.clients = clients;
        this.authenticationHelper = authenticationHelper;
        this.events = events;
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
        this.properties = properties;
        this.holders = holders;
    }

    /** What the ownership check found: whether this is the newest version, and how many keys a rename rewrote. */
    private record Binding(boolean newest, int renamed) {
    }

    @Transactional
    public RouteRegistrationResultDTO register(RouteRegistrationRequestDTO registration) {
        String service = registration.getService();
        Map<String, RegisteredRouteDTO> routes = validatedRoutes(service, registration);
        String clientId = callerClientId();
        ApplicationEntity application = applicationOf(clientId);

        jdbc.queryForObject("SELECT pg_advisory_xact_lock(?, hashtext(?))::text", String.class, LOCK_CLASS, clientId);
        Binding binding = bindOwner(service, clientId, registration.getVersion());

        // Declared, so not dead, whatever the version. First: a deletion the job is
        // committing right now waits for it, and the read below then sees DELETED.
        if (!routes.isEmpty()) {
            named.update(CANCEL, Map.of("keys", routes.keySet()));
        }
        Map<String, PermissionEntity> existing = permissions.findByPermissionKeyIn(routes.keySet()).stream()
                .collect(Collectors.toMap(PermissionEntity::getPermissionKey, Function.identity()));
        int created = 0;
        for (RegisteredRouteDTO route : routes.values()) {
            PermissionEntity permission = existing.get(route.getKey());
            if (permission == null) {
                permissions.save(newPermission(service, application, registration.getVersion(), route));
            } else if (permission.getStatus() == Status.DELETED) {
                // Deleted after its grace period, declared again (a rollback): back, with no assignments.
                permission.setStatus(Status.ACTIVE);
                permission.setApplication(application);
                permission.setLastSeenVersion(registration.getVersion());
            } else {
                continue;
            }
            events.publishSettingsAudit(new PermissionRegisteredEvent(route.getKey(), service));
            created++;
        }
        permissions.flush();
        int pending = 0;
        if (binding.newest()) {
            permissions.markSeenInVersion(routes.keySet(), registration.getVersion());
            schedule(service, registration.getVersion(), routes.keySet());
            pending = Objects.requireNonNullElse(jdbc.queryForObject(PENDING_COUNT, Integer.class, service), 0);
        }

        LOGGER.info("[ROUTES] {} {} registered {} routes: {} created, {} unchanged, {} pending deletion, {} renamed",
                service, registration.getVersion(), routes.size(), created, routes.size() - created, pending,
                binding.renamed());
        return new RouteRegistrationResultDTO(created, routes.size() - created, pending, binding.renamed());
    }

    /** Schedules the service's live keys this registration does not declare. */
    private void schedule(String service, String version, Set<String> declared) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("service", service);
        parameters.put("version", version);
        parameters.put("grace", (double) properties.getDeletionGrace().toSeconds());
        // NOT IN () is invalid SQL: with nothing declared, no key matches it.
        parameters.put("keys", declared.isEmpty() ? Set.of("") : declared);
        named.update(SCHEDULE, parameters);
    }

    /** Checks the service name and every key; handlers sharing one key form one route (first one wins). */
    private static Map<String, RegisteredRouteDTO> validatedRoutes(String service,
                                                                   RouteRegistrationRequestDTO registration) {
        if (!RouteKeys.isValidService(service)) {
            throw IgrpResponseStatusException.badRequest("Invalid service name " + service
                    + ": 1 to 120 letters, digits, '-' or '_', and not the reserved " + RouteKeys.RESERVED_SERVICE);
        }
        // Handlers whose derived keys coincide are one permission: the same key
        // is derived on every request, so one assignment protects them all.
        Map<String, RegisteredRouteDTO> routes = new LinkedHashMap<>();
        for (RegisteredRouteDTO route : registration.getRoutes()) {
            if (route == null) {
                throw IgrpResponseStatusException.badRequest("Invalid route registration: a route is null");
            }
            String problem = RouteKeys.problem(service, route.getKey());
            if (problem == null) {
                problem = RouteKeys.nameProblem(route.getName());
            }
            if (problem != null) {
                throw IgrpResponseStatusException.badRequest("Invalid route registration: " + problem);
            }
            routes.putIfAbsent(route.getKey(), route);
        }
        return routes;
    }

    private String callerClientId() {
        String clientId = authenticationHelper.getJwtToken().getClaimAsString(ServiceAccountTokenClaims.CLAIM_CLIENT_ID);
        if (clientId == null || clientId.isBlank()) {
            throw IgrpResponseStatusException.forbidden("Route registration needs a service account token");
        }
        return clientId;
    }

    private ApplicationEntity applicationOf(String clientId) {
        return clients.findByClientId(clientId)
                .map(OAuthClientEntity::getApplication)
                .orElseThrow(() -> IgrpResponseStatusException.of(HttpStatus.CONFLICT,
                        "Client " + clientId + " is not linked to an application, so its routes could not be "
                                + "granted. Link the client to its application in App Center, then restart the service."));
    }

    /**
     * Binds the service to the calling client on first sight, renames the
     * client's service when it registers under a new name, and checks the
     * owner on every later registration.
     */
    private Binding bindOwner(String service, String clientId, String version) {
        Instant now = Instant.now();
        RegisteredServiceEntity registered = services.findById(service).orElse(null);
        int renamed = 0;
        if (registered == null) {
            Optional<RegisteredServiceEntity> previous = services.findByOwnerClientId(clientId);
            if (previous.isEmpty()) {
                services.save(new RegisteredServiceEntity(service, clientId, version, now));
                return new Binding(true, 0);
            }
            RegisteredServiceEntity before = previous.get();
            if (ServiceVersions.compare(version, before.getLastVersion()) < 0) {
                // An old pod still running under the old name: a rename goes one way only.
                throw IgrpResponseStatusException.of(HttpStatus.CONFLICT, "Service " + service + " was renamed to "
                        + before.getService() + " by a newer version (" + before.getLastVersion()
                        + "); version " + version + " cannot rename it back");
            }
            renamed = rename(before.getService(), service);
            registered = services.findById(service).orElseThrow();
        }
        if (!registered.getOwnerClientId().equals(clientId)) {
            throw IgrpResponseStatusException.forbidden(
                    "Service " + service + " is registered by another client");
        }
        if (ServiceVersions.compare(version, registered.getLastVersion()) < 0) {
            return new Binding(false, renamed);
        }
        registered.registered(version, now);
        return new Binding(true, renamed);
    }

    /**
     * Rewrites every key of {@code from} with the {@code to} prefix. The rows
     * are the same, so every assignment moves with them; a name that was the
     * key follows it. Holders' cached sets are evicted after commit.
     *
     * @return how many keys were rewritten
     */
    private int rename(String from, String to) {
        // The pending rows first: the deletion job locks them before the keys, so this order cannot deadlock it.
        jdbc.update("UPDATE t_permission_pending_deletion SET service = ? WHERE service = ?", to, from);
        List<Map<String, Object>> keys = Objects.requireNonNullElse(jdbc.queryForList(
                "SELECT id, permission_key, status FROM t_permission WHERE service = ? AND permission_key IS NOT NULL",
                from), List.of());
        Map<String, String> renamedKeys = new LinkedHashMap<>();
        for (Map<String, Object> row : keys) {
            String key = (String) row.get("permission_key");
            if (key.startsWith(from + ".")) {
                renamedKeys.put(key, to + key.substring(from.length()));
            }
        }
        List<String> taken = renamedKeys.isEmpty() ? List.of() : Objects.requireNonNullElse(named.queryForList(
                "SELECT permission_key FROM t_permission WHERE permission_key IN (:keys)",
                Map.of("keys", renamedKeys.values()), String.class), List.of());
        if (!taken.isEmpty()) {
            throw IgrpResponseStatusException.of(HttpStatus.CONFLICT, "Renaming " + from + " to " + to
                    + " would rewrite keys that already exist: " + String.join(", ", taken));
        }
        int live = 0;
        for (Map<String, Object> row : keys) {
            String key = (String) row.get("permission_key");
            String renamedKey = renamedKeys.get(key);
            if (renamedKey == null) {
                continue;
            }
            int id = ((Number) row.get("id")).intValue();
            holders.evictAfterCommit(id, RolePermissionChangedEvent.CHANGE_KEYS_RENAMED);
            jdbc.update(RENAME_KEY, renamedKey, to, renamedKey, id);
            // Deleted keys move too, so the prefix stays whole; only live ones count and are audited.
            if (!Status.DELETED.name().equals(row.get("status"))) {
                events.publishSettingsAudit(new PermissionKeyRenamedEvent(key, renamedKey, to));
                live++;
            }
        }
        jdbc.update("UPDATE t_registered_service SET service = ? WHERE service = ?", to, from);
        LOGGER.info("[ROUTES] Renamed service {} to {}: {} live key(s) rewritten", from, to, live);
        return live;
    }

    private static PermissionEntity newPermission(String service, ApplicationEntity application, String version,
                                                  RegisteredRouteDTO route) {
        PermissionEntity permission = new PermissionEntity();
        permission.setPermissionKey(route.getKey());
        permission.setName(route.getName() != null && !route.getName().isBlank() ? route.getName() : route.getKey());
        permission.setDescription(route.getDescription());
        permission.setStatus(Status.ACTIVE);
        permission.setService(service);
        permission.setApplication(application);
        permission.setLastSeenVersion(version);
        return permission;
    }
}
