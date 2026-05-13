package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.users.application.commands.AddRolesToUserCommand;
import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.role.domain.service.RoleValidator;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.exceptions.NoActionPerformedException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.UserRoleAssignmentRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.UserRoleAssignment;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.UserRoleId;
import cv.igrp.platform.access_management.shared.domain.events.EventPublisher;
import cv.igrp.platform.access_management.shared.domain.events.UserRoleChangedEvent;
import cv.igrp.platform.access_management.users.infrastructure.service.ExpireRoleService;
import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditService;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditCategory;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditEventType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Command handler responsible for associating a {@link RoleEntity} with a specific {@link IGRPUserEntity}.
 * <p>
 * This handler processes the {@link AddRolesToUserCommand} by:
 * <ul>
 *   <li>Fetching the user from the {@link IGRPUserEntityRepository} using the provided user ID.</li>
 *   <li>Fetching the role from the {@link RoleEntityRepository} using the provided role ID.</li>
 *   <li>Adding the user to the role's user set (initializing it if necessary).</li>
 *   <li>Saving the updated role and mapping it to a {@link RoleDTO}.</li>
 *   <li>Returning the mapped role in a {@link ResponseEntity} with HTTP status 201 (Created).</li>
 * </ul>
 *
 * @see AddRolesToUserCommand
 * @see IGRPUserEntityRepository
 * @see RoleEntityRepository
 * @see RoleMapper
 * @see RoleDTO
 */
@Component
public class AddRolesToUserCommandHandler implements CommandHandler<AddRolesToUserCommand, ResponseEntity<?>> {

   private static final Logger logger = LoggerFactory.getLogger(AddRolesToUserCommandHandler.class);

   private final IGRPUserEntityRepository userRepository;
   private final RoleEntityRepository roleRepository;
   private final DepartmentEntityRepository departmentRepository;
   private final RoleMapper roleMapper;
   private final UserRoleAssignmentRepository userRoleAssignmentRepository;
   private final ExpireRoleService expireRoleService;
   private final SecurityAuditService securityAuditService;
   private final EventPublisher eventPublisher;

   /**
    * Constructs the handler with required dependencies.
    *
    * @param userRepository the repository used to retrieve user entities
    * @param roleRepository the repository used to retrieve and update roles
    * @param departmentRepository the repository used to retrieve department entities
    * @param roleMapper the mapper used to convert role entities to DTOs
    * @param userRoleAssignmentRepository the repository for user role assignments
    * @param expireRoleService the service for managing role expiration
    * @param securityAuditService the service for security auditing
    */
   public AddRolesToUserCommandHandler(
           IGRPUserEntityRepository userRepository,
           RoleEntityRepository roleRepository,
           DepartmentEntityRepository departmentRepository,
           RoleMapper roleMapper,
           UserRoleAssignmentRepository userRoleAssignmentRepository,
           ExpireRoleService expireRoleService,
           SecurityAuditService securityAuditService,
           EventPublisher eventPublisher) {
      this.userRepository = userRepository;
      this.roleRepository = roleRepository;
      this.departmentRepository = departmentRepository;
      this.roleMapper = roleMapper;
      this.userRoleAssignmentRepository = userRoleAssignmentRepository;
      this.expireRoleService = expireRoleService;
      this.securityAuditService = securityAuditService;
      this.eventPublisher = eventPublisher;
   }

   /**
    * Handles the command to add a role to a user.
    *
    * @param command the command containing the user ID and RoleUserDTO to associate the corresponding ID
    * @return a {@link ResponseEntity} containing a list with the updated {@link RoleDTO}
    * @throws IgrpResponseStatusException if the user or role is not found
    */
   @IgrpCommandHandler
   @Transactional
   public ResponseEntity<List<RoleDTO>> handle(AddRolesToUserCommand command) {
      String userId = command.getId();
      List<String> rolesToAdd = command.getAddRolesToUserRequest();
      String departmentCode = command.getDepartmentCode();

      if (rolesToAdd.isEmpty())
         throw new NoActionPerformedException("No action performed because the role list is empty");

      DepartmentEntity department = departmentRepository.findByCodeAndStatusNotDeleted(departmentCode);

      List<RoleEntity> successfullyAssignedRoles = new ArrayList<>();

      IGRPUserEntity user = userRepository.findById(userId)
              .orElseThrow(() -> {
                 logger.warn("User not found with ID={}", userId);
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND,"Invalid User ID",
                         "User not found with ID: %s".formatted(userId));
              });

      for (String role : command.getAddRolesToUserRequest()) {

         logger.info("Assigning role name={} to user ID={}", role, userId);
         RoleEntity roleEntity = roleRepository.findByDepartmentAndCodeAndStatusNot(department, role, Status.DELETED)
                 .orElseThrow(() -> {
                    logger.warn("Role not found with code={}", role);
                    return IgrpResponseStatusException.of(
                            HttpStatus.NOT_FOUND, "Invalid Role code",
                            "Role not found with code: %s".formatted(role));
                 });

         // 1. Search in the user's existing collection to avoid NonUniqueObjectException
         Optional<UserRoleAssignment> existingUraOpt = user.getUserRoleAssignments().stream()
                 .filter(assignment -> assignment.getRole().getId().equals(roleEntity.getId()))
                 .findFirst();

         final UserRoleAssignment ura;
         if (existingUraOpt.isPresent()) {
             // 2. Update the instance already managed by Hibernate in the session
             ura = existingUraOpt.get();
             ura.setAssignedAt(java.time.LocalDateTime.now());
             ura.setExpiresAt(command.getExpiresAt());
         } else {
             // 3. Create only if truly new
             ura = new UserRoleAssignment(user, roleEntity, command.getExpiresAt());
             ura.setAssignedAt(java.time.LocalDateTime.now());
             user.getUserRoleAssignments().add(ura);
         }

         // Let Hibernate's CascadeType.ALL handle the persistence automatically
         expireRoleService.scheduleExpiration(ura);
         successfullyAssignedRoles.add(roleEntity);

         Map<String, Object> auditContext = new HashMap<>();
         auditContext.put("userId", user.getExternalId());
         auditContext.put("roleCode", roleEntity.getCode());
         auditContext.put("expiresAt", command.getExpiresAt());
         securityAuditService.logEvent(AuditEventType.ROLE_ASSIGNED, AuditCategory.PRIVILEGE, auditContext);
      }

      if (!successfullyAssignedRoles.isEmpty()) {
         java.util.Set<String> roleCodes = successfullyAssignedRoles.stream()
                 .map(RoleEntity::getCode)
                 .collect(Collectors.toSet());
         eventPublisher.publishUserRoleChanged(new UserRoleChangedEvent(
                 userId, roleCodes, departmentCode, UserRoleChangedEvent.CHANGE_ADDED, null));
      }

      // Return the assigned roles mapped to DTO. Assuming successfully assigned roles will not have expiresAt returned natively via mapToDto(RoleEntity) unless modified, but we can do our best.
      List<RoleDTO> rolesDTO = successfullyAssignedRoles.stream().map(r -> {
         RoleDTO dto = roleMapper.mapToDto(r);
         dto.setExpiresAt(command.getExpiresAt());
         return dto;
      }).toList();

      return ResponseEntity.status(HttpStatus.CREATED).body(rolesDTO);

   }

}