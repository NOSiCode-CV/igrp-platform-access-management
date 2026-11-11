package cv.igrp.platform.access_management.permission_fetch.domain.service;

import cv.igrp.platform.access_management.active_role.application.dto.ActiveRoleDTO.Source;
import cv.igrp.platform.access_management.active_role.domain.service.ActiveRoleService;
import cv.igrp.platform.access_management.active_role.domain.service.ActiveRoleService.ResolvedActiveRole;
import cv.igrp.platform.access_management.permission_fetch.application.dto.PermissionSetDTO;
import cv.igrp.platform.access_management.permission_fetch.infrastructure.cache.PermissionSetCache;
import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditService;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditCategory;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditEventType;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import cv.igrp.platform.access_management.shared.security.ServiceAccountTokenClaims;
import cv.igrp.platform.access_management.shared.security.SubjectParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The permission fetch (v3-06, spec "Permission fetch contract"): what the
 * caller may do in one application, written to the shared Redis where every
 * service reads it.
 *
 * <ul>
 *   <li>A user gets the keys of their active role in the application (#13):
 *       none when they have no active role; every key of the application for
 *       root.</li>
 *   <li>A service account ("me" is the service account) gets the keys of its
 *       roles plus its direct grants, in the application.</li>
 * </ul>
 * A caller without an active authorization gets 401 and nothing is written:
 * the user must be active (and the session filter has already required a
 * live session); the service account and its client must be active.
 * {@code APPLICATION_ACCESSED} is audited at most once per user, application
 * and TTL. While Redis is down the fetch still answers, from the database.
 */
@Service
public class PermissionFetchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionFetchService.class);

    /** Every scope is {@code all} until scopes on role permissions land (#21). */
    static final String DEFAULT_SCOPE = "all";

    private static final String ROLE_KEYS = """
            SELECT DISTINCT p.permission_key FROM t_permission p
              JOIN t_role_permission rp ON rp.permission = p.id
             WHERE rp.role_id = ? AND p.application_id = ?
               AND p.permission_key IS NOT NULL AND p.status = 'ACTIVE'
             ORDER BY 1""";

    private static final String APPLICATION_KEYS = """
            SELECT p.permission_key FROM t_permission p
             WHERE p.application_id = ?
               AND p.permission_key IS NOT NULL AND p.status = 'ACTIVE'
             ORDER BY 1""";

    private static final String SERVICE_ACCOUNT_KEYS = """
            SELECT DISTINCT p.permission_key FROM t_permission p
             WHERE p.application_id = ?
               AND p.permission_key IS NOT NULL AND p.status = 'ACTIVE'
               AND (p.id IN (SELECT rp.permission FROM t_service_account_role_assignment sara
                               JOIN t_role r ON r.id = sara.role_id
                               JOIN t_role_permission rp ON rp.role_id = r.id
                              WHERE sara.service_account_id = ?::uuid AND r.status = 'ACTIVE'
                                AND (sara.expires_at IS NULL OR sara.expires_at > now()))
                    OR p.id IN (SELECT g.permission_id FROM t_service_account_permission_grant g
                                 WHERE g.service_account_id = ?::uuid))
             ORDER BY 1""";

    private static final String ACTIVE_SERVICE_ACCOUNT = """
            SELECT EXISTS (SELECT 1 FROM t_service_account sa JOIN t_oauth_client oc ON oc.id = sa.oauth_client_id
                            WHERE sa.id = ?::uuid AND sa.active = TRUE AND oc.active = TRUE)""";

    private final JdbcTemplate jdbc;
    private final ActiveRoleService activeRoles;
    private final PermissionSetCache cache;
    private final AuthenticationHelper authenticationHelper;
    private final SecurityAuditService audit;

    public PermissionFetchService(JdbcTemplate jdbc,
                                  ActiveRoleService activeRoles,
                                  PermissionSetCache cache,
                                  AuthenticationHelper authenticationHelper,
                                  SecurityAuditService audit) {
        this.jdbc = jdbc;
        this.activeRoles = activeRoles;
        this.cache = cache;
        this.authenticationHelper = authenticationHelper;
        this.audit = audit;
    }

    public PermissionSetDTO fetch(String applicationCode) {
        Jwt jwt = authenticationHelper.getJwtToken();
        String sub = jwt.getSubject();
        boolean serviceAccount = ServiceAccountTokenClaims.PRINCIPAL_TYPE_SERVICE_ACCOUNT
                .equals(jwt.getClaimAsString(ServiceAccountTokenClaims.CLAIM_PRINCIPAL_TYPE));
        List<String> keys = serviceAccount
                ? serviceAccountKeys(sub, applicationCode)
                : userKeys(sub, applicationCode);

        Map<String, String> scopes = new LinkedHashMap<>();
        keys.forEach(key -> scopes.put(key, DEFAULT_SCOPE));
        if (cacheAndCheckFirstAccess(sub, applicationCode, scopes)) {
            audit.logEvent(AuditEventType.APPLICATION_ACCESSED, AuditCategory.AUTHORIZATION,
                    Map.of("applicationCode", applicationCode));
        }
        return new PermissionSetDTO(keys, scopes);
    }

    /**
     * Writes the set. While Redis is down the starter calls this endpoint on
     * every request (spec "Cache"), so the fetch still answers from the
     * database; the access is then not audited, as it cannot be counted once
     * per TTL.
     */
    private boolean cacheAndCheckFirstAccess(String sub, String applicationCode, Map<String, String> scopes) {
        try {
            return cache.write(sub, applicationCode, scopes);
        } catch (DataAccessException e) {
            LOGGER.warn("[PERMISSIONS] Redis unavailable; serving {} for {} without caching: {}",
                    applicationCode, sub, e.getMessage());
            return false;
        }
    }

    private List<String> userKeys(String sub, String applicationCode) {
        String userId = SubjectParser.parseUserSubjectOrThrow(sub);
        if (!Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM t_user WHERE id = ? AND status = 'ACTIVE')", Boolean.class, userId))) {
            throw unauthorized("The user is not active");
        }
        // resolve() also answers 404 for an unknown application.
        Optional<ResolvedActiveRole> role = activeRoles.resolve(userId, applicationCode);
        if (role.isEmpty()) {
            return List.of();
        }
        ResolvedActiveRole active = role.get();
        if (active.source() == Source.SUPER_ADMIN) {
            return jdbc.queryForList(APPLICATION_KEYS, String.class, active.applicationId());
        }
        return jdbc.queryForList(ROLE_KEYS, String.class, active.roleId(), active.applicationId());
    }

    private List<String> serviceAccountKeys(String serviceAccountId, String applicationCode) {
        if (!isUuid(serviceAccountId)) {
            throw unauthorized("The service account token has no service account subject");
        }
        if (!Boolean.TRUE.equals(jdbc.queryForObject(ACTIVE_SERVICE_ACCOUNT, Boolean.class, serviceAccountId))) {
            throw unauthorized("The service account or its client is not active");
        }
        int applicationId = applicationId(applicationCode);
        return jdbc.queryForList(SERVICE_ACCOUNT_KEYS, String.class, applicationId, serviceAccountId, serviceAccountId);
    }

    private int applicationId(String applicationCode) {
        List<Integer> ids = jdbc.queryForList(
                "SELECT id FROM t_application WHERE code = ? AND status = 'ACTIVE'", Integer.class, applicationCode);
        if (ids.isEmpty()) {
            throw IgrpResponseStatusException.notFound("Application " + applicationCode + " not found");
        }
        return ids.getFirst();
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
    }

    private static IgrpResponseStatusException unauthorized(String reason) {
        return IgrpResponseStatusException.of(HttpStatus.UNAUTHORIZED, "No active authorization", reason);
    }
}
