package cv.igrp.platform.access_management.authorization.domain.service;


import cv.igrp.framework.auth.core.authorization.model.PermissionCheckRequest;
import cv.igrp.platform.access_management.authorization.application.dto.PermissionCacheEntryDTO;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class PermissionCacheService {

    private static final String CACHE_NAME = "permissionCache";

    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionCacheService.class);

    private final JdbcTemplate jdbcTemplate;

    @Getter
    private boolean fromCache;

    public PermissionCacheService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }


    @Cacheable(value = CACHE_NAME,
            key = "#request.subject + ':' + #request.resource + ':' + #request.action")
    public PermissionCacheEntryDTO getOrLoadPermission(PermissionCheckRequest request) {

        LOGGER.info("Cache MISS - Buscando no banco de dados: {}:{}:{}",
                request.getSubject(),
                request.getResource(),
                request.getAction());

        return checkInternal(request);
    }

    public PermissionCacheEntryDTO checkInternal(PermissionCheckRequest request) {
        setFromCacheAsFalse();

        String subject = request.getSubject();
        String resource = request.getResource();
        String action = request.getAction();

        boolean allowed = checkPermission(subject, resource, action);

        PermissionCacheEntryDTO response = new PermissionCacheEntryDTO(
                                                allowed,
                                                Collections.emptyList(),
                                                allowed ? "Permission granted" : "Permission denied");
        LOGGER.debug("Authorization result for '{}': {}", subject, response);
        return response;
    }

    private Boolean checkPermission(String username, String resourceItem, String permissionName) {
        String sql = """
                WITH target_user AS (
                            SELECT id
                            FROM t_user
                            WHERE username = ?
                        ),
                        user_roles AS (
                            SELECT ru.roles_id
                            FROM t_role_users ru
                            JOIN target_user tu ON ru.users_id = tu.id
                        ),
                        role_permissions AS (
                            SELECT rp.permission
                            FROM t_role_permission rp
                            JOIN user_roles ur ON rp.role_id = ur.roles_id
                        ),
                        target_permission AS (
                            SELECT p.id
                            FROM t_permission p
                            JOIN role_permissions rp ON p.id = rp.permission
                            WHERE p.name = ?
                        ),
                        resource_check AS (
                            SELECT 1 AS result
                            FROM t_resource_item ri
                            JOIN target_permission tp ON ri.permission_id = tp.id
                            WHERE ri.name = ?
                        )
                        SELECT result FROM resource_check
                        LIMIT 1;
               """;

        List<Integer> results = jdbcTemplate.query(sql, (_,_) -> 1, username, permissionName, resourceItem);

        return !results.isEmpty();
    }

    private void setFromCacheAsFalse() {
        this.fromCache = false;
    }
    public void setFromCacheAsTrue() {
        this.fromCache = true;
    }
}
