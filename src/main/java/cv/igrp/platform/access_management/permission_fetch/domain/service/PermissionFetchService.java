package cv.igrp.platform.access_management.permission_fetch.domain.service;

import cv.igrp.platform.access_management.active_role.application.dto.ActiveRoleDTO.Source;
import cv.igrp.platform.access_management.active_role.domain.service.ActiveRoleService;
import cv.igrp.platform.access_management.active_role.domain.service.ActiveRoleService.ResolvedActiveRole;
import cv.igrp.platform.access_management.permission_fetch.application.dto.PermissionSetDTO;
import cv.igrp.platform.access_management.permission_fetch.infrastructure.cache.PermissionSetCache;
import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditService;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditCategory;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditEventType;
import cv.igrp.platform.access_management.shared.application.constants.PermissionScope;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import cv.igrp.platform.access_management.shared.security.ServiceAccountTokenClaims;
import cv.igrp.platform.access_management.shared.security.SubjectParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    /** Key and scope of each permission of the active role in the application (#21). */
    private static final String ROLE_KEYS = """
            SELECT p.permission_key, rp.scope FROM t_permission p
              JOIN t_role_permission rp ON rp.permission = p.id
             WHERE rp.role_id = ? AND p.application_id = ?
               AND p.permission_key IS NOT NULL AND p.status = 'ACTIVE'
             ORDER BY 1""";

    /** Root: every key of the application, scope all. */
    private static final String APPLICATION_KEYS = """
            SELECT p.permission_key, 'all' AS scope FROM t_permission p
             WHERE p.application_id = ?
               AND p.permission_key IS NOT NULL AND p.status = 'ACTIVE'
             ORDER BY 1""";

    /**
     * A service account: each role's permissions with their scope, and its
     * direct grants, which have no role and so are scope all. A key reached
     * several ways appears once per way; the fetch combines them.
     */
    private static final String SERVICE_ACCOUNT_KEYS = """
            SELECT p.permission_key, rp.scope FROM t_permission p
              JOIN t_role_permission rp ON rp.permission = p.id
              JOIN t_role r ON r.id = rp.role_id
              JOIN t_service_account_role_assignment sara ON sara.role_id = r.id
             WHERE p.application_id = ? AND sara.service_account_id = ?::uuid
               AND p.permission_key IS NOT NULL AND p.status = 'ACTIVE' AND r.status = 'ACTIVE'
               AND (sara.expires_at IS NULL OR sara.expires_at > now())
            UNION ALL
            SELECT p.permission_key, 'all' FROM t_permission p
              JOIN t_service_account_permission_grant g ON g.permission_id = p.id
             WHERE p.application_id = ? AND g.service_account_id = ?::uuid
               AND p.permission_key IS NOT NULL AND p.status = 'ACTIVE'
             ORDER BY 1""";

    /** A department and every department under it, by code (#39). */
    private static final String SUBTREE = """
            WITH RECURSIVE tree AS (
                SELECT d.id, d.code FROM t_department d WHERE d.code = ? AND d.status <> 'DELETED'
                UNION
                SELECT c.id, c.code FROM t_department c JOIN tree t ON c.parent_id = t.id WHERE c.status <> 'DELETED')
            SELECT code FROM tree ORDER BY code""";

    /**
     * The roles a service account holds now in the application (#39): in an
     * active department linked to it, as a user's valid roles are.
     */
    private static final String SERVICE_ACCOUNT_ROLES = """
            SELECT DISTINCT r.code FROM t_service_account_role_assignment sara
              JOIN t_role r ON r.id = sara.role_id
              JOIN t_department d ON d.id = r.department
              JOIN t_department_application da ON da.department_id = d.id
             WHERE sara.service_account_id = ?::uuid AND da.application_id = ?
               AND r.status = 'ACTIVE' AND d.status = 'ACTIVE'
               AND (sara.expires_at IS NULL OR sara.expires_at > now())
             ORDER BY 1""";

    /** The union of the subtrees under those roles' departments (#39). */
    private static final String SERVICE_ACCOUNT_DEPARTMENTS = """
            WITH RECURSIVE tree AS (
                SELECT d.id, d.code FROM t_department d
                  JOIN t_role r ON r.department = d.id
                  JOIN t_service_account_role_assignment sara ON sara.role_id = r.id
                  JOIN t_department_application da ON da.department_id = d.id
                 WHERE sara.service_account_id = ?::uuid AND da.application_id = ?
                   AND r.status = 'ACTIVE' AND d.status = 'ACTIVE'
                   AND (sara.expires_at IS NULL OR sara.expires_at > now())
                UNION
                SELECT c.id, c.code FROM t_department c JOIN tree t ON c.parent_id = t.id WHERE c.status <> 'DELETED')
            SELECT DISTINCT code FROM tree ORDER BY code""";

    /** What a caller may do in one application, before the scopes are combined. */
    private record Computed(List<ScopedKey> rows, List<String> roles, List<String> departments) {

        static final Computed NOTHING = new Computed(List.of(), List.of(), List.of());

        PermissionSetDTO toSet() {
            Map<String, String> scopes = scopesByKey(rows);
            return new PermissionSetDTO(List.copyOf(scopes.keySet()), scopes, roles, departments);
        }
    }

    private static final RowMapper<ScopedKey> SCOPED_KEY = (rs, i) ->
            new ScopedKey(rs.getString(1), PermissionScope.fromCode(rs.getString(2)));

    /** One row of the queries above. */
    private record ScopedKey(String key, PermissionScope scope) {
    }

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
        PermissionSetDTO set = (serviceAccount
                ? serviceAccountSet(sub, applicationCode)
                : userSet(sub, applicationCode)).toSet();
        if (cacheAndCheckFirstAccess(sub, applicationCode, set)) {
            audit.logEvent(AuditEventType.APPLICATION_ACCESSED, AuditCategory.AUTHORIZATION,
                    Map.of("applicationCode", applicationCode));
        }
        return set;
    }

    /**
     * An administrator's refresh (#19): recomputes a user's set for the
     * application from their current roles, exactly as their own fetch would,
     * and writes it. Nothing comes from the caller. Not an access, so not
     * audited as one.
     *
     * Like a cache miss, it writes only for a user who is still signed in: a
     * killed or logged-out user is not given a key back.
     *
     * @throws IgrpResponseStatusException 404 for an unknown user or application;
     *                                     409 when the user is not active or not signed in (nothing written);
     *                                     503 when Redis is unavailable
     */
    public PermissionSetDTO refresh(String userId, String applicationCode) {
        List<String> status = list("SELECT status FROM t_user WHERE id = ? AND status <> 'DELETED'",
                String.class, userId);
        if (status.isEmpty()) {
            throw IgrpResponseStatusException.notFound("User " + userId + " not found");
        }
        // Until sessions are replaced by AM's authorization store (#33), a live
        // sign-in is an active session.
        if (!"ACTIVE".equals(status.getFirst()) || !Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM t_user_session WHERE user_id = ? AND status = 'ACTIVE')",
                Boolean.class, userId))) {
            throw IgrpResponseStatusException.of(HttpStatus.CONFLICT, "No active authorization",
                    "The user is not active or not signed in; nothing was written");
        }
        PermissionSetDTO set = userSet(userId, applicationCode).toSet();
        try {
            cache.writeForRefresh(userId, applicationCode, set);
        } catch (DataAccessException e) {
            throw IgrpResponseStatusException.of(HttpStatus.SERVICE_UNAVAILABLE, "Permission cache unavailable",
                    "Redis is unavailable; nothing was written");
        }
        return set;
    }

    /** A key reached through several roles gets the union of their scopes. */
    private static Map<String, String> scopesByKey(List<ScopedKey> rows) {
        Map<String, List<PermissionScope>> byKey = new LinkedHashMap<>();
        rows.forEach(row -> byKey.computeIfAbsent(row.key(), k -> new ArrayList<>()).add(row.scope()));
        Map<String, String> scopes = new LinkedHashMap<>();
        byKey.forEach((key, keyScopes) -> scopes.put(key, PermissionScope.union(keyScopes)));
        return scopes;
    }

    /**
     * Writes the set. While Redis is down the starter calls this endpoint on
     * every request (spec "Cache"), so the fetch still answers from the
     * database; the access is then not audited, as it cannot be counted once
     * per TTL.
     */
    private boolean cacheAndCheckFirstAccess(String sub, String applicationCode, PermissionSetDTO set) {
        try {
            return cache.write(sub, applicationCode, set);
        } catch (DataAccessException e) {
            LOGGER.warn("[PERMISSIONS] Redis unavailable; serving {} for {} without caching: {}",
                    applicationCode, sub, e.getMessage());
            return false;
        }
    }

    private Computed userSet(String sub, String applicationCode) {
        String userId = SubjectParser.parseUserSubjectOrThrow(sub);
        if (!Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM t_user WHERE id = ? AND status = 'ACTIVE')", Boolean.class, userId))) {
            throw unauthorized("The user is not active");
        }
        // resolve() also answers 404 for an unknown application.
        Optional<ResolvedActiveRole> role = activeRoles.resolve(userId, applicationCode);
        if (role.isEmpty()) {
            return Computed.NOTHING;
        }
        ResolvedActiveRole active = role.get();
        if (active.source() == Source.SUPER_ADMIN) {
            // Root's scope is all on every key, so no rows are filtered: no departments to compare.
            return new Computed(rows(APPLICATION_KEYS, active.applicationId()), List.of(active.code()), List.of());
        }
        return new Computed(rows(ROLE_KEYS, active.roleId(), active.applicationId()), List.of(active.code()),
                list(SUBTREE, String.class, active.departmentCode()));
    }

    private Computed serviceAccountSet(String serviceAccountId, String applicationCode) {
        if (!isUuid(serviceAccountId)) {
            throw unauthorized("The service account token has no service account subject");
        }
        if (!Boolean.TRUE.equals(jdbc.queryForObject(ACTIVE_SERVICE_ACCOUNT, Boolean.class, serviceAccountId))) {
            throw unauthorized("The service account or its client is not active");
        }
        int applicationId = applicationId(applicationCode);
        return new Computed(rows(SERVICE_ACCOUNT_KEYS, applicationId, serviceAccountId, applicationId, serviceAccountId),
                list(SERVICE_ACCOUNT_ROLES, String.class, serviceAccountId, applicationId),
                list(SERVICE_ACCOUNT_DEPARTMENTS, String.class, serviceAccountId, applicationId));
    }

    private int applicationId(String applicationCode) {
        List<Integer> ids = list(
                "SELECT id FROM t_application WHERE code = ? AND status = 'ACTIVE'", Integer.class, applicationCode);
        if (ids.isEmpty()) {
            throw IgrpResponseStatusException.notFound("Application " + applicationCode + " not found");
        }
        return ids.getFirst();
    }

    private List<ScopedKey> rows(String sql, Object... args) {
        return jdbc.query(sql, SCOPED_KEY, args);
    }

    /** {@code queryForList} is declared nullable; an absent result is an empty list. */
    private <T> List<T> list(String sql, Class<T> type, Object... args) {
        return Objects.requireNonNullElse(jdbc.queryForList(sql, type, args), List.of());
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
