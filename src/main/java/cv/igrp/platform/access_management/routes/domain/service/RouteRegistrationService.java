package cv.igrp.platform.access_management.routes.domain.service;

import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.OAuthClientEntity;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.OAuthClientJpaRepository;
import cv.igrp.platform.access_management.routes.application.dto.RouteRegistrationRequestDTO;
import cv.igrp.platform.access_management.routes.application.dto.RouteRegistrationRequestDTO.RegisteredRouteDTO;
import cv.igrp.platform.access_management.routes.application.dto.RouteRegistrationResultDTO;
import cv.igrp.platform.access_management.routes.infrastructure.persistence.RegisteredServiceEntity;
import cv.igrp.platform.access_management.routes.infrastructure.persistence.RegisteredServiceRepository;
import cv.igrp.platform.access_management.security_audit.domain.events.PermissionRegisteredEvent;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Turns a service's routes into permissions (v3-02, spec "Route registration
 * contract"): keys not seen before are created with no assignments; existing
 * keys keep their name, description and assignments.
 *
 * <p>Ownership: the first registration of a service binds it to the calling
 * client, and only that client may register it again. Keys belong to the
 * calling client's application; a client without one is refused, because its
 * keys could never be granted.
 *
 * <p>Replicas of a service register at the same moment on a deploy: a
 * transaction-scoped advisory lock per service serialises them, so each key is
 * created once and the counts are right. The unique index on the key is the
 * backstop.
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

    public RouteRegistrationService(PermissionEntityRepository permissions,
                                    RegisteredServiceRepository services,
                                    OAuthClientJpaRepository clients,
                                    AuthenticationHelper authenticationHelper,
                                    EventPublisher events,
                                    JdbcTemplate jdbc) {
        this.permissions = permissions;
        this.services = services;
        this.clients = clients;
        this.authenticationHelper = authenticationHelper;
        this.events = events;
        this.jdbc = jdbc;
    }

    @Transactional
    public RouteRegistrationResultDTO register(RouteRegistrationRequestDTO registration) {
        String service = registration.getService();
        Map<String, RegisteredRouteDTO> routes = validatedRoutes(service, registration);
        String clientId = callerClientId();
        ApplicationEntity application = applicationOf(clientId);

        jdbc.queryForObject("SELECT pg_advisory_xact_lock(?, hashtext(?))::text", String.class, LOCK_CLASS, service);
        boolean newest = bindOwner(service, clientId, registration.getVersion());

        Set<String> existing = permissions.findByPermissionKeyIn(routes.keySet()).stream()
                .map(PermissionEntity::getPermissionKey)
                .collect(Collectors.toSet());
        int created = 0;
        for (RegisteredRouteDTO route : routes.values()) {
            if (!existing.contains(route.getKey())) {
                permissions.save(newPermission(service, application, registration.getVersion(), route));
                events.publishSettingsAudit(new PermissionRegisteredEvent(route.getKey(), service));
                created++;
            }
        }
        permissions.flush();
        // An older replica restarting mid-deploy must not move the marker back.
        if (newest) {
            permissions.markSeenInVersion(routes.keySet(), registration.getVersion());
        }

        LOGGER.info("[ROUTES] {} {} registered {} routes: {} created, {} unchanged",
                service, registration.getVersion(), routes.size(), created, routes.size() - created);
        return new RouteRegistrationResultDTO(created, routes.size() - created, 0, 0);
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
     * Binds the service to the calling client on first sight, and checks it on
     * every later registration.
     *
     * @return whether this registration's version is the newest seen for the service
     */
    private boolean bindOwner(String service, String clientId, String version) {
        Instant now = Instant.now();
        RegisteredServiceEntity registered = services.findById(service).orElse(null);
        if (registered == null) {
            services.findByOwnerClientId(clientId).ifPresent(other -> {
                throw IgrpResponseStatusException.of(HttpStatus.CONFLICT, "Client " + clientId
                        + " already registers service " + other.getService() + ". Renaming a service is not "
                        + "supported yet; register it under " + other.getService() + ".");
            });
            services.save(new RegisteredServiceEntity(service, clientId, version, now));
            return true;
        }
        if (!registered.getOwnerClientId().equals(clientId)) {
            throw IgrpResponseStatusException.forbidden(
                    "Service " + service + " is registered by another client");
        }
        if (ServiceVersions.compare(version, registered.getLastVersion()) < 0) {
            return false;
        }
        registered.registered(version, now);
        return true;
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
