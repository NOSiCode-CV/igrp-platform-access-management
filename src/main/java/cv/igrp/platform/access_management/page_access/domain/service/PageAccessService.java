package cv.igrp.platform.access_management.page_access.domain.service;

import cv.igrp.platform.access_management.active_role.application.dto.ActiveRoleDTO;
import cv.igrp.platform.access_management.active_role.domain.service.ActiveRoleService;
import cv.igrp.platform.access_management.page_access.application.dto.PageAccessDecisionDTO;
import cv.igrp.platform.access_management.page_access.application.dto.PageAccessDecisionDTO.Reason;
import cv.igrp.platform.access_management.permission_fetch.domain.service.PermissionFetchService;
import cv.igrp.platform.access_management.permission_fetch.infrastructure.cache.PermissionSetCache;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import cv.igrp.platform.access_management.shared.security.ServiceAccountTokenClaims;
import cv.igrp.platform.access_management.shared.security.SubjectParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The page check (v3-26, #34; spec "Frontend"): may the caller open a page of
 * a frontend application? Default deny. In order, the first failure decides:
 * <ol>
 *   <li>a service account gets no page;</li>
 *   <li>an inactive user is not authorized (401);</li>
 *   <li>the caller needs a role valid for the application (an unknown or
 *       inactive application answers the same, so codes cannot be probed);</li>
 *   <li>a registered page of the application must serve the path, for root
 *       too: a page without a declaration is never rendered;</li>
 *   <li>root is then allowed;</li>
 *   <li>the caller needs an active role (several valid: they pick one);</li>
 *   <li>its permission set must hold every required key of the page.</li>
 * </ol>
 * The permission set is the one services read: the cached set when there is
 * one, otherwise {@link PermissionFetchService}, so a page and the APIs it
 * calls agree.
 */
@Service
public class PageAccessService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PageAccessService.class);

    /** The application's live pages, each with its required keys. */
    private static final String PAGES = """
            SELECT p.page_key, p.path,
                   string_agg(pp.permission_key, ',' ORDER BY pp.permission_key) FILTER (WHERE pp.required) AS required
              FROM t_application_page p
              JOIN t_application a ON a.id = p.application_id
              LEFT JOIN t_application_page_permission pp ON pp.page_id = p.id
             WHERE a.code = ? AND p.status = 'ACTIVE'
             GROUP BY p.id, p.page_key, p.path
             ORDER BY p.id""";

    private static final String ACTIVE_APPLICATION =
            "SELECT EXISTS (SELECT 1 FROM t_application WHERE code = ? AND status = 'ACTIVE')";

    private static final String ACTIVE_USER = "SELECT EXISTS (SELECT 1 FROM t_user WHERE id = ? AND status = 'ACTIVE')";

    private record Page(String key, List<String> required) {
    }

    private final ActiveRoleService activeRoles;
    private final PermissionFetchService fetches;
    private final AuthenticationHelper authenticationHelper;
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;

    public PageAccessService(ActiveRoleService activeRoles, PermissionFetchService fetches,
                             AuthenticationHelper authenticationHelper, JdbcTemplate jdbc,
                             StringRedisTemplate redis) {
        this.activeRoles = activeRoles;
        this.fetches = fetches;
        this.authenticationHelper = authenticationHelper;
        this.jdbc = jdbc;
        this.redis = redis;
    }

    @Transactional(readOnly = true)
    public PageAccessDecisionDTO check(String applicationCode, String path) {
        Jwt jwt = authenticationHelper.getJwtToken();
        if (ServiceAccountTokenClaims.PRINCIPAL_TYPE_SERVICE_ACCOUNT
                .equals(jwt.getClaimAsString(ServiceAccountTokenClaims.CLAIM_PRINCIPAL_TYPE))) {
            return PageAccessDecisionDTO.denied(Reason.NOT_A_USER);
        }
        String userId = SubjectParser.parseUserSubjectOrThrow(jwt.getSubject());
        if (!Boolean.TRUE.equals(jdbc.queryForObject(ACTIVE_USER, Boolean.class, userId))) {
            throw IgrpResponseStatusException.of(HttpStatus.UNAUTHORIZED, "The user is not active");
        }
        // Checked here: an exception from current() would mark this transaction rollback-only.
        if (!Boolean.TRUE.equals(jdbc.queryForObject(ACTIVE_APPLICATION, Boolean.class, applicationCode))) {
            return PageAccessDecisionDTO.denied(Reason.NO_APPLICATION_ACCESS);
        }
        ActiveRoleDTO role = activeRoles.current(applicationCode);
        boolean root = role.source() == ActiveRoleDTO.Source.SUPER_ADMIN;
        if (!root && role.availableRoles().isEmpty()) {
            return PageAccessDecisionDTO.denied(Reason.NO_APPLICATION_ACCESS);
        }
        Map<String, Page> pages = pages(applicationCode);
        Page page = PagePaths.match(path, pages.keySet()).map(pages::get).orElse(null);
        if (page == null) {
            return PageAccessDecisionDTO.denied(Reason.PAGE_NOT_REGISTERED);
        }
        if (root) {
            return new PageAccessDecisionDTO(true, Reason.ROOT, page.key(), List.of());
        }
        if (role.activeRole() == null) {
            return new PageAccessDecisionDTO(false, Reason.NO_ACTIVE_ROLE, page.key(), List.of());
        }
        Set<String> held = permissionSet(jwt, userId, applicationCode);
        List<String> missing = page.required().stream().filter(key -> !held.contains(key)).toList();
        if (!missing.isEmpty()) {
            return new PageAccessDecisionDTO(false, Reason.MISSING_PERMISSIONS, page.key(), missing);
        }
        return new PageAccessDecisionDTO(true, Reason.ALLOWED, page.key(), List.of());
    }

    /** The application's live pages by path; the first registered wins a duplicate path. */
    private Map<String, Page> pages(String applicationCode) {
        Map<String, Page> byPath = new LinkedHashMap<>();
        jdbc.query(PAGES, rs -> {
            String required = rs.getString("required");
            byPath.putIfAbsent(rs.getString("path"), new Page(rs.getString("page_key"),
                    required == null ? List.of() : Arrays.asList(required.split(","))));
        }, applicationCode);
        return byPath;
    }

    /** The cached set when there is one (the marker present); otherwise the fetch, which caches it. */
    private Set<String> permissionSet(Jwt jwt, String userId, String applicationCode) {
        try {
            Set<String> cached = redis.opsForSet().members(PermissionSetCache.setKey(userId, applicationCode));
            if (cached.contains(PermissionSetCache.MARKER)) {
                Set<String> keys = new HashSet<>(cached);
                keys.remove(PermissionSetCache.MARKER);
                return keys;
            }
        } catch (RuntimeException ex) {
            LOGGER.warn("[PAGES] Permission cache unavailable, computing the set of {}: {}", userId, ex.getMessage());
        }
        return new HashSet<>(fetches.fetch(jwt, applicationCode).keys());
    }
}
