package cv.igrp.platform.access_management.authorization.domain.service;


import cv.igrp.framework.auth.core.authorization.model.PermissionCheckRequest;
import cv.igrp.platform.access_management.authorization.application.dto.PermissionCacheEntryDTO;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static cv.igrp.platform.access_management.shared.infrastructure.service.ConfigurationService.SUPER_ADMIN_ROLE;

@Service
public class PermissionCacheService {

    private static final String CACHE_NAME = "permissionCache";

    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionCacheService.class);

    private final JdbcTemplate jdbcTemplate;

    private final IGRPUserEntityRepository userRepository;

    @Getter
    private boolean fromCache;

    public PermissionCacheService(JdbcTemplate jdbcTemplate, IGRPUserEntityRepository userRepository) {
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
    }


    /**
     * Entry point. Superadmin grants are NOT cached — they're resolved fresh
     * on every call so that promoting a user to superadmin (or fixing an
     * existing one whose assignment was missing) takes effect immediately
     * without a cache flush. Regular permission decisions go through the
     * cached path below.
     */
    public PermissionCacheEntryDTO getOrLoadPermission(PermissionCheckRequest request) {
        if (isSuperAdmin(request.getSubject())) {
            setFromCacheAsFalse();
            return new PermissionCacheEntryDTO(true, Collections.emptyList(),
                    "Permission granted (superadmin)");
        }
        return getOrLoadPermissionCached(request);
    }

    @Cacheable(value = CACHE_NAME,
            keyGenerator = "permissionCacheKeyGenerator")
    public PermissionCacheEntryDTO getOrLoadPermissionCached(PermissionCheckRequest request) {

        LOGGER.info("Cache MISS - Checking in database: {}:{}:{}",
                request.getSubject(),
                request.getResource(),
                request.getAction());

        return checkInternal(request);
    }

    @Transactional(readOnly = true)
    public boolean isSuperAdmin(String subject) {
        if (subject == null || subject.isBlank()) {
            return false;
        }
        // 1) In-memory check off the eagerly-fetched user graph
        var userOpt = userRepository.findByExternalIdWithRolesAndPermissions(subject);
        if (userOpt.isPresent()
                && userOpt.get().getStatus() != Status.DELETED
                && userOpt.get().getStatus() != Status.INACTIVE) {
            var user = userOpt.get();
            boolean hasRole = user.getRoles().stream()
                    .map(r -> r != null ? r.getCode() : null)
                    .filter(Objects::nonNull)
                    .anyMatch(SUPER_ADMIN_ROLE::equals);
            if (hasRole) {
                LOGGER.debug("Superadmin shortcut hit (entity) for subject '{}' (id={})",
                        subject, user.getId());
                return true;
            }
            // 2) Defensive SQL fallback in case the user-role collection was
            // not populated (e.g. detached entity / async edge case).
            String sql = """
                    SELECT 1 FROM t_user_role_assignment ura
                    JOIN t_role r ON r.id = ura.role_id
                    WHERE ura.user_id = ? AND r.code = ?
                      AND (ura.expires_at IS NULL OR ura.expires_at > NOW())
                    LIMIT 1
                    """;
            List<Integer> rows = jdbcTemplate.query(sql, (rs, rowNum) -> 1,
                    user.getId(), SUPER_ADMIN_ROLE);
            if (!rows.isEmpty()) {
                LOGGER.debug("Superadmin shortcut hit (SQL) for subject '{}' (id={})",
                        subject, user.getId());
                return true;
            }
        }
        // 3) Last-resort fallback by external_id: handles the case where the
        // user record cannot be loaded through the JPA fetch graph at all.
        String sqlByExternalId = """
                SELECT 1 FROM t_user u
                JOIN t_user_role_assignment ura ON ura.user_id = u.id
                JOIN t_role r ON r.id = ura.role_id
                WHERE u.external_id = ? AND r.code = ?
                  AND u.status NOT IN ('DELETED', 'INACTIVE')
                  AND (ura.expires_at IS NULL OR ura.expires_at > NOW())
                LIMIT 1
                """;
        List<Integer> rows = jdbcTemplate.query(sqlByExternalId, (rs, rowNum) -> 1,
                subject, SUPER_ADMIN_ROLE);
        return !rows.isEmpty();
    }

    @Transactional(readOnly = true)
    public PermissionCacheEntryDTO checkInternal(PermissionCheckRequest request) {
        setFromCacheAsFalse();

        String subject = request.getSubject();
        String action = request.getAction();

        boolean allowed = checkPermission(subject, action);

        PermissionCacheEntryDTO response = new PermissionCacheEntryDTO(
                                                allowed,
                                                Collections.emptyList(),
                                                allowed ? "Permission granted" : "Permission denied");
        LOGGER.debug("Authorization result for '{}': {}", subject, response);
        return response;
    }

    private Boolean checkPermission(String subject, String permissionName) {
        // Superadmin is resolved by the non-cached entry-point in
        // getOrLoadPermission(...). By the time we reach here we are doing a
        // regular role/permission match.

        // Verifies if the user exists or if it is deleted or disabled
        var userOpt = userRepository.findByExternalIdWithRolesAndPermissions(subject);
        if (userOpt.isEmpty() || userOpt.get().getStatus() == Status.DELETED || userOpt.get().getStatus() == Status.INACTIVE) {
            LOGGER.info("User '{}' not found or not active for permission check '{}'", subject, permissionName);
            return false;
        }

        var user = userOpt.get();

        LOGGER.debug("Checking permission '{}' for user '{}' (id={}, activeRole={})",
                permissionName, subject, user.getId(),
                user.getActiveRole() != null ? user.getActiveRole().getCode() : "<none>");

        String sql = """
                 SELECT 1 AS result
                        FROM t_user u
                        JOIN t_user_role_assignment ura ON ura.user_id = u.id
                        JOIN t_role_permission rp ON rp.role_id = ura.role_id
                        JOIN t_role r ON r.id = ura.role_id
                        JOIN t_permission p ON p.id = rp.permission
                        WHERE u.external_id = ?
                          AND p.name = ?
                          AND p.status = 'ACTIVE'
                          AND r.status = 'ACTIVE'
                          AND r.id = u.active_role_id
                          AND (ura.expires_at IS NULL OR ura.expires_at > NOW())
                        LIMIT 1;
                """;

        List<Integer> results = jdbcTemplate.query(sql, (rs, rowNum) -> 1, subject, permissionName);

        return !results.isEmpty();
    }

    private void setFromCacheAsFalse() {
        this.fromCache = false;
    }
    public void setFromCacheAsTrue() {
        this.fromCache = true;
    }
}
