package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.auth.core.adapter.IAdapter;
import cv.igrp.framework.auth.core.exception.IAMException;
import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.role.domain.service.RoleValidator;
import cv.igrp.platform.access_management.shared.application.constants.Status;
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
public class UpdateUserStatusCommandHandler implements CommandHandler<UpdateUserStatusCommand, ResponseEntity<IGRPUserDTO>> {

   private static final Logger LOGGER = LoggerFactory.getLogger(UpdateUserStatusCommandHandler.class);

   private static final String ACTIVE_STATUS = "ACTIVE";
   private static final String INACTIVE_STATUS = "INACTIVE";

   private final IGRPUserEntityRepository userRepository;
   private final JdbcTemplate jdbcTemplate;
   private final IGRPUserMapper userMapper;
   private final IAdapter adapter;
   
   public UpdateUserStatusCommandHandler(
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
    * Handles the status update of an existing user.
    *
    * @param command the command containing the user ID and the new status
    * @return a {@link ResponseEntity} containing the updated {@link IGRPUserDTO}
    * @throws EntityNotFoundException if no user exists with the given ID
    */
   @Transactional
   @IgrpCommandHandler
   public ResponseEntity<IGRPUserDTO> handle(UpdateUserStatusCommand command) {

      Integer userId = command.getId();

      LOGGER.info("Updating user with ID={}", userId);

      IGRPUserEntity user = userRepository.findById(userId)
              .orElseThrow(() -> {
                 LOGGER.warn("User with ID={} not found", userId);
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND,
                         "Invalid User",
                         "User not found with ID: " + userId);
              });

      Status status = Status.fromCodeOrThrow(command.getValue());

      String oldStatus = user.getStatus().getCode();
      String newStatus = status.getCode();

      // Store current roles if status is changing from ACTIVE to INACTIVE
      Map<String, Set<String>> userRolesBackup;
      userRolesBackup = getUserRolesFromDatabase(userId);
      LOGGER.info("Fetching roles for user {}: {}", userId, userRolesBackup);
      
      // Handle role assignments based on status change
      handleRoleAssignmentsOnStatusChange(user, oldStatus, newStatus, userRolesBackup);

      user.setStatus(status);

      var updatedUser = userRepository.save(user);

      LOGGER.info("User status updated successfully: id={}, email={}", updatedUser.getId(), updatedUser.getEmail());

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
            LOGGER.info("Removed all roles from deactivated user: {}", user.getEmail());
         }
         // Case 2: User is being reactivated (INACTIVE -> ACTIVE)
         else if (INACTIVE_STATUS.equals(oldStatus) && ACTIVE_STATUS.equals(newStatus)) {
            restoreRolesToUser(user.getExternalId(), userRolesBackup);
            LOGGER.info("Restored roles to reactivated user: {}", user.getEmail());
         }
         // Case 3: Status unchanged or other transitions - no role changes needed
         else {
            LOGGER.debug("No role assignment changes needed for user: {}, status: {} -> {}",
                    user.getEmail(), oldStatus, newStatus);
         }
      } catch (Exception e) {
         LOGGER.error("Failed to handle role assignments for user {} during status change from {} to {}: {}",
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
                  LOGGER.debug("Removed role {} from user {} in department {}",
                          roleName, externalId, departmentCode);
               } catch (IAMException e) {
                  LOGGER.warn("Failed to remove role {} from user {} in department {}: {}",
                          roleName, externalId, departmentCode, e.getMessage());
               }
            }
         }

         LOGGER.info("Completed removing all roles from user: {}", externalId);
      } catch (IAMException e) {
         LOGGER.error("Failed to get user roles from provider for user {}: {}", externalId, e.getMessage());
      }
   }

   /**
    * Restore roles to a reactivated user
    */
   private void restoreRolesToUser(String username, Map<String, Set<String>> userRolesBackup) {
      if (userRolesBackup == null || userRolesBackup.isEmpty()) {
         LOGGER.info("No roles to restore for user: {}", username);
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
               LOGGER.debug("Restored role {} to user {} in department {}",
                       roleName, username, departmentCode);
            } catch (IAMException e) {
               LOGGER.warn("Failed to restore role {} to user {} in department {}: {}",
                       roleName, username, departmentCode, e.getMessage());
            }
         }
      }

      LOGGER.info("Restored {} roles to user: {}", restoredRoles, username);
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
         LOGGER.error("Failed to get user roles from database for user {}: {}", userId, e.getMessage());
      }

      return result;
   }
   
}