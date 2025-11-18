package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.auth.core.adapter.IAdapter;
import cv.igrp.framework.auth.core.exception.IAMException;
import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.role.domain.service.RoleValidator;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.users.mapper.IGRPUserMapper;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Command handler responsible for updating an existing {@link IGRPUserEntity} entity.
 * <p>
 * This handler processes {@link UpdateUserCommand} instances, which include the user ID and
 * an {@link IGRPUserDTO} containing the updated user data.
 * <p>
 * The handler performs the following:
 * <ul>
 *   <li>Retrieves the user entity by ID from the {@link IGRPUserEntityRepository}.</li>
 *   <li>Updates the user entity's fields only if the corresponding fields in the DTO are non-null.</li>
 *   <li>Manages role assignments in the IAM provider when user status changes</li>
 *   <li>Saves the updated entity using the repository.</li>
 *   <li>Maps the updated entity to a DTO and returns it in a {@link ResponseEntity}.</li>
 * </ul>
 *
 * <p>Defensive checks are applied to ensure existing data is not overwritten by {@code null} values.</p>
 *
 * @see UpdateUserCommand
 * @see IGRPUserDTO
 * @see IGRPUserEntityRepository
 * @see IGRPUserMapper
 */
@Component
public class UpdateUserCommandHandler implements CommandHandler<UpdateUserCommand, ResponseEntity<IGRPUserDTO>> {

    private static final Logger logger = LoggerFactory.getLogger(UpdateUserCommandHandler.class);
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String INACTIVE_STATUS = "INACTIVE";

    private final IGRPUserEntityRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final IGRPUserMapper userMapper;
    private final IAdapter adapter;

    /**
     * Constructs the handler with required dependencies.
     *
     * @param userRepository the repository to retrieve and save user entities
     * @param userMapper     the mapper used to convert entities to DTOs
     * @param adapter        the IAM adapter for managing role assignments
     */
    public UpdateUserCommandHandler(
            IGRPUserEntityRepository userRepository,
            JdbcTemplate jdbcTemplate,
            IGRPUserMapper userMapper,
            IAdapter adapter
    ) {
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.userMapper = userMapper;
        this.adapter = adapter;
    }

    /**
     * Handles the update of an existing user.
     *
     * @param command the command containing the user ID and the updated user data
     * @return a {@link ResponseEntity} containing the updated {@link IGRPUserDTO}
     * @throws EntityNotFoundException if no user exists with the given ID
     */
    @IgrpCommandHandler
    @Transactional
    public ResponseEntity<IGRPUserDTO> handle(UpdateUserCommand command) {
        Integer userId = command.getId();

        logger.info("Updating user with ID={}", userId);

        IGRPUserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    logger.warn("User with ID={} not found", userId);
                    return IgrpResponseStatusException.of(
                            HttpStatus.NOT_FOUND,
                            "Invalid User",
                            "User not found with ID: " + userId);
                });

        IGRPUserDTO dto = command.getIgrpuserdto();
        String oldStatus = user.getStatus().getCode();
        String newStatus = dto.getStatus().getCode();

        // Store current roles if status is changing from ACTIVE to INACTIVE
        Map<String, Set<String>> userRolesBackup = new HashMap<>();
        userRolesBackup = getUserRolesFromDatabase(userId);
        logger.info("Fetching roles for user {}: {}", userId, userRolesBackup);

        // Update user fields
        if (dto.getName() != null) {
            user.setName(dto.getName());
        }

        if (dto.getEmail() != null) {
            user.setEmail(dto.getEmail());
        }

        if (dto.getPicture() != null) {
            user.setPicture(dto.getPicture());
        }

        if (dto.getSignature() != null) {
            user.setSignature(dto.getSignature());
        }

        if (dto.getStatus() != null) {
            user.setStatus(dto.getStatus());
        }

        var updatedUser = userRepository.save(user);

        // Handle role assignments based on status change
        handleRoleAssignmentsOnStatusChange(user, oldStatus, newStatus, userRolesBackup);

        logger.info("User updated successfully: id={}, email={}", updatedUser.getId(), updatedUser.getEmail());

        return ResponseEntity.ok(userMapper.toDto(updatedUser));
    }

    /**
     * Handles role assignments when user status changes
     */
    private void handleRoleAssignmentsOnStatusChange(IGRPUserEntity user, String oldStatus, String newStatus,
                                                     Map<String, Set<String>> userRolesBackup) {
        try {
            // Case 1: User is being deactivated (ACTIVE -> INACTIVE)
            if (ACTIVE_STATUS.equals(oldStatus) && INACTIVE_STATUS.equals(newStatus)) {
                removeAllRolesFromUser(user.getExternalId());
                logger.info("Removed all roles from deactivated user: {}", user.getEmail());
            }
            // Case 2: User is being reactivated (INACTIVE -> ACTIVE)
            else if (INACTIVE_STATUS.equals(oldStatus) && ACTIVE_STATUS.equals(newStatus)) {
                restoreRolesToUser(user.getExternalId(), userRolesBackup);
                logger.info("Restored roles to reactivated user: {}", user.getEmail());
            }
            // Case 3: Status unchanged or other transitions - no role changes needed
            else {
                logger.debug("No role assignment changes needed for user: {}, status: {} -> {}",
                        user.getEmail(), oldStatus, newStatus);
            }
        } catch (Exception e) {
            logger.error("Failed to handle role assignments for user {} during status change from {} to {}: {}",
                    user.getEmail(), oldStatus, newStatus, e.getMessage(), e);
            // Don't throw exception to avoid rolling back the user status update
            // The synchronization service will handle any inconsistencies
        }
    }

    /**
     * Remove all roles from a user in the IAM provider
     */
    private void removeAllRolesFromUser(String externalId) {
        try {
            // Get current roles from provider
            Map<String, Map<String, Set<String>>> providerUserRoles = adapter.getAllUserRoles();
            Map<String, Set<String>> userRoles = providerUserRoles.getOrDefault(externalId, new HashMap<>());

            // Remove all roles across all departments
            for (Map.Entry<String, Set<String>> deptEntry : userRoles.entrySet()) {
                String departmentCode = deptEntry.getKey();
                Set<String> roleNames = deptEntry.getValue();

                for (String roleName : roleNames) {
                    try {
                        adapter.unassignRoleFromUser(departmentCode, roleName, externalId);
                        logger.debug("Removed role {} from user {} in department {}",
                                roleName, externalId, departmentCode);
                    } catch (IAMException e) {
                        logger.warn("Failed to remove role {} from user {} in department {}: {}",
                                roleName, externalId, departmentCode, e.getMessage());
                    }
                }
            }

            logger.info("Completed removing all roles from user: {}", externalId);
        } catch (IAMException e) {
            logger.error("Failed to get user roles from provider for user {}: {}", externalId, e.getMessage());
        }
    }

    /**
     * Restore roles to a reactivated user
     */
    private void restoreRolesToUser(String username, Map<String, Set<String>> userRolesBackup) {
        if (userRolesBackup == null || userRolesBackup.isEmpty()) {
            logger.info("No roles to restore for user: {}", username);
            return;
        }

        int restoredRoles = 0;
        for (Map.Entry<String, Set<String>> deptEntry : userRolesBackup.entrySet()) {
            String departmentCode = deptEntry.getKey();
            Set<String> roleNames = deptEntry.getValue();

            for (String roleName : roleNames) {
                try {
                    adapter.assignRoleToUser(departmentCode, roleName, username);
                    restoredRoles++;
                    logger.debug("Restored role {} to user {} in department {}",
                            roleName, username, departmentCode);
                } catch (IAMException e) {
                    logger.warn("Failed to restore role {} to user {} in department {}: {}",
                            roleName, username, departmentCode, e.getMessage());
                }
            }
        }

        logger.info("Restored {} roles to user: {}", restoredRoles, username);
    }

    /**
     * Get user roles from database for backup purposes
     */
    private Map<String, Set<String>> getUserRolesFromDatabase(Integer userId) {
        // This should match the logic in SynchronizationService but for a single user
        // You might want to extract this to a shared service to avoid duplication

        String sql = """
                SELECT d.code as department_code, r.name as role_name
                FROM t_role_users ru
                LEFT JOIN t_user u ON ru.users_id = u.id
                LEFT JOIN t_role r ON ru.roles_id = r.id
                LEFT JOIN t_department d ON r.department = d.id
                WHERE u.id = ? AND r.status = ? AND d.status = ?
                """;

        Map<String, Set<String>> result = new HashMap<>();

        try {
            jdbcTemplate.query(sql, (rs, _) -> {
                String departmentCode = rs.getString("department_code");
                String roleName = rs.getString("role_name");

                result.computeIfAbsent(departmentCode, _ -> new HashSet<>())
                        .add(RoleValidator.normalizeRoleCodeForAdapter(roleName, departmentCode));
                return null;
            }, userId, ACTIVE_STATUS, ACTIVE_STATUS);
        } catch (Exception e) {
            logger.error("Failed to get user roles from database for user {}: {}", userId, e.getMessage());
        }

        return result;
    }
}