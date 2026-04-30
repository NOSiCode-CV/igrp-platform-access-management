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


    @Cacheable(value = CACHE_NAME,
            keyGenerator = "permissionCacheKeyGenerator")
    public PermissionCacheEntryDTO getOrLoadPermission(PermissionCheckRequest request) {

        LOGGER.info("Cache MISS - Checking in database: {}:{}:{}",
                request.getSubject(),
                request.getResource(),
                request.getAction());

        return checkInternal(request);
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

        // Verifies if the user exists or if it is deleted or disabled
        var userOpt = userRepository.findByExternalIdWithRolesAndPermissions(subject);
        if (userOpt.isEmpty() || userOpt.get().getStatus() == Status.DELETED || userOpt.get().getStatus() == Status.INACTIVE) {
            LOGGER.info("User {} is not active or deleted", subject);
            return false;
        }

        // If the user is superadmin it is allowed to do anything
        // Using direct SQL check to avoid LazyInitializationException in async threads
        String superAdminSql = """
                SELECT 1 FROM t_role_users ru
                JOIN t_role r ON r.id = ru.roles_id
                WHERE ru.users_id = CAST(? AS integer) AND r.code = ?
                LIMIT 1
                """;
        List<Integer> superAdminResults = jdbcTemplate.query(superAdminSql, (rs, rowNum) -> 1, userOpt.get().getId(), SUPER_ADMIN_ROLE);

        if (!superAdminResults.isEmpty()) {
            LOGGER.info("User {} is superadmin", subject);
            return true;
        }

        LOGGER.info("Checking permission {} for user {}", permissionName, subject);

        String sql = """
                 SELECT 1 AS result
                        FROM t_user u
                        JOIN t_role_users ru ON ru.users_id = u.id
                        JOIN t_role_permission rp ON rp.role_id = ru.roles_id
                        JOIN t_role r ON r.id = ru.roles_id
                        JOIN t_permission p ON p.id = rp.permission
                        WHERE u.external_id = ?
                          AND p.name = ?
                          AND p.status = 'ACTIVE'
                          AND r.status = 'ACTIVE'
                          AND r.id = u.active_role_id
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
