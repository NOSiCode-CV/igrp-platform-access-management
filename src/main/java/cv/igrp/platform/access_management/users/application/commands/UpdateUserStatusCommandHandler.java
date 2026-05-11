package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditService;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.utils.UserUtils;
import cv.igrp.platform.access_management.users.mapper.IGRPUserMapper;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import cv.igrp.platform.access_management.shared.domain.events.EventPublisher;
import cv.igrp.platform.access_management.shared.domain.events.UserStatusChangedEvent;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

@Component
public class UpdateUserStatusCommandHandler implements CommandHandler<UpdateUserStatusCommand, ResponseEntity<IGRPUserDTO>> {

   private static final Logger LOGGER = LoggerFactory.getLogger(UpdateUserStatusCommandHandler.class);

   private final IGRPUserEntityRepository userRepository;
   private final IGRPUserMapper userMapper;
   private final UserUtils utils;
   private final SecurityAuditService auditService;
   private final EventPublisher eventPublisher;


   public UpdateUserStatusCommandHandler(
           IGRPUserEntityRepository userRepository,
           IGRPUserMapper userMapper,
           UserUtils utils,
           SecurityAuditService auditService,
           EventPublisher eventPublisher
   ) {
       this.userRepository = userRepository;
       this.userMapper = userMapper;
       this.utils = utils;
       this.auditService = auditService;
       this.eventPublisher = eventPublisher;
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
      userRolesBackup = utils.getUserRolesFromDatabase(userId);
      LOGGER.info("Fetching roles for user {}: {}", userId, userRolesBackup);
      
      // Handle role assignments based on status change
      utils.handleRoleAssignmentsOnStatusChange(user, oldStatus, newStatus, userRolesBackup);

      user.setStatus(status);

      var updatedUser = userRepository.save(user);

      auditService.logUserChange(updatedUser.getId(), newStatus);

      if (!oldStatus.equals(newStatus)) {
         eventPublisher.publishUserStatusChanged(new UserStatusChangedEvent(
                 userId, oldStatus, newStatus, null));
      }

      LOGGER.info("User status updated successfully: id={}, email={}", updatedUser.getId(), updatedUser.getEmail());

      return ResponseEntity.ok(userMapper.toDto(updatedUser));

   }
   
}