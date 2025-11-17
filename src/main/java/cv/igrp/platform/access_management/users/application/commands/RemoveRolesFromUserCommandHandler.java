package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.auth.core.adapter.IAdapter;
import cv.igrp.framework.auth.core.exception.IAMException;
import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RemoveRolesFromUserCommandHandler implements CommandHandler<RemoveRolesFromUserCommand, ResponseEntity<List<RoleDTO>>> {

   private static final Logger logger = LoggerFactory.getLogger(RemoveRolesFromUserCommandHandler.class);

   private final IGRPUserEntityRepository userRepository;
   private final RoleEntityRepository roleRepository;
   private final IAdapter adapter;

   /**
    * Constructs the handler with the required repository dependency.
    *
    * @param userRepository the repository used to retrieve and save {@link IGRPUserEntity} entities
    * @param roleRepository the repository used to retrieve and save {@link RoleEntity} entities
    * @param adapter the adapter to assign a role to user in iam
    */
   public RemoveRolesFromUserCommandHandler(
           IGRPUserEntityRepository userRepository, RoleEntityRepository roleRepository, IAdapter adapter) {
      this.userRepository = userRepository;
      this.roleRepository = roleRepository;
      this.adapter = adapter;
   }

   /**
    * Handles the removal of one or more roles from a user.
    *
    * @param command the command containing the user ID and the list of role IDs to remove
    * @return a {@link ResponseEntity} containing the updated list of the user's {@link RoleDTO}s
    * @throws IgrpResponseStatusException if no user is found with the given ID
    */
   @IgrpCommandHandler
   @Transactional
   public ResponseEntity<List<RoleDTO>> handle(RemoveRolesFromUserCommand command) {
      Integer userId = command.getId();
      List<String> roleIdsToRemove = command.getRemoveRolesFromUserRequest();

      logger.info("Attempting to remove roles {} from id={}", roleIdsToRemove, userId);

      IGRPUserEntity user = userRepository.findById(userId)
              .orElseThrow(() -> {
                 logger.warn("User not found with id={}", userId);
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND,
                         "Invalid ID",
                         "User not found with ID: %s".formatted(userId));
              });

      if (roleIdsToRemove != null && !roleIdsToRemove.isEmpty()) {

         List<RoleEntity> rolesToRemove = user.getRoles().stream()
                 .filter(role -> roleIdsToRemove.contains(role.getCode()))
                 .toList();

         if (!rolesToRemove.isEmpty()) {

            // Remove association both ways to ensure consistency
            for (var role : rolesToRemove) {
               role.getUsers().remove(user);
            }
            // Also remove roles from user's collection
            user.getRoles().removeAll(rolesToRemove);

            // Persist changes to the user only when roles were actually removed
            userRepository.save(user);

            logger.info("Roles removed successfully from user ID={}", userId);

            for (RoleEntity role : rolesToRemove) {
               try {
                  adapter.unassignRoleFromUser(role.getDepartment().getCode(), role.getCode(), user.getExternalId());
                  logger.info("Role code={} from department with code {} unassigned to user sub={} in Keycloak",
                          role.getCode(),
                          role.getDepartment().getCode(),
                          user.getExternalId());
               } catch(IAMException e){
                  logger.error("Failed to unassign role code={} from {} department to user sub={} in Keycloak: {}",
                          role.getCode(),
                          role.getDepartment().getCode(),
                          user.getExternalId(),
                          e.getMessage(), e);
                  throw IgrpResponseStatusException.of(
                          HttpStatus.INTERNAL_SERVER_ERROR,
                          "Remove Roles from User Failed",
                          e.getMessage()
                  );
               }
            }
         }
          else {
            logger.info("No matching roles found to remove for user ID={}", userId);
         }

      } else {
         logger.info("No roles provided for removal for user ID={}", userId);
      }


      List<RoleDTO> result = user.getRoles().stream()
              .map(role -> new RoleDTO(role.getId(), role.getCode(),
                      role.getName(), role.getDescription(),
                      null, null,
                      null, null, null))
              .collect(Collectors.toList());

      logger.info("Returning {} remaining roles for user ID={}", result.size(), userId);

      return ResponseEntity.ok(result);
   }

}