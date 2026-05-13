package cv.igrp.platform.access_management.shared.infrastructure.utils;

import cv.igrp.platform.access_management.role.domain.service.RoleValidator;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
public class UserUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserUtils.class);

    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String INACTIVE_STATUS = "INACTIVE";

    private final JdbcTemplate jdbcTemplate;

    public UserUtils(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Handles role assignments when user status changes
     * For no-adapter architecture, no provider calls are made.
     */
    public void handleRoleAssignmentsOnStatusChange(IGRPUserEntity user, String oldStatus, String newStatus,
                                                    Map<String, Set<String>> userRolesBackup) {
        // No provider interaction needed in no-adapter architecture
        LOGGER.debug("No role assignment changes needed for user: {}, status: {} -> {}",
                user.getEmail(), oldStatus, newStatus);
    }

    /**
     * Get user roles from database for backup purposes
     */
    public Map<String, Set<String>> getUserRolesFromDatabase(String userId) {
        // This should match the logic in SynchronizationService but for a single user
        // You might want to extract this to a shared service to avoid duplication

        String sql = """
                SELECT d.code as department_code, r.name as role_name
                FROM t_user_role_assignment ura
                LEFT JOIN t_user u ON ura.user_id = u.id
                LEFT JOIN t_role r ON ura.role_id = r.id
                LEFT JOIN t_department d ON r.department = d.id
                WHERE u.id = ? AND r.status = ? AND d.status = ?
                  AND (ura.expires_at IS NULL OR ura.expires_at > NOW())
                """;

        Map<String, Set<String>> result = new HashMap<>();

        try {
            jdbcTemplate.query(sql, (rs, rowNum) -> {
                String departmentCode = rs.getString("department_code");
                String roleName = rs.getString("role_name");

                result.computeIfAbsent(departmentCode, key -> new HashSet<>())
                        .add(RoleValidator.normalizeRoleCodeForAdapter(roleName, departmentCode));
                return null;
            }, userId, ACTIVE_STATUS, ACTIVE_STATUS);
        } catch (Exception e) {
            LOGGER.error("Failed to get user roles from database for user {}: {}", userId, e.getMessage());
        }

        return result;
    }

    public String constructInvitationUrl(String appCenterUrl, String token) {

        if (appCenterUrl.isBlank())
            throw IgrpResponseStatusException.of(
                    HttpStatus.BAD_REQUEST,
                    "App Center URL is not configured",
                    "Please configure the IGRP_APP_CENTER_URL environment variable."
            );

        return "%s/invite/accept?token=%s".formatted(appCenterUrl, token);

    }

}
