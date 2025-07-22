package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;

@Component
public class RemoveRolesFromUserCommandHandler implements CommandHandler<RemoveRolesFromUserCommand, ResponseEntity<List<RoleDTO>>> {

   private static final Logger logger = LoggerFactory.getLogger(RemoveRolesFromUserCommandHandler.class);

   private final IGRPUserEntityRepository userRepository;

   /**
    * Constructs the handler with the required repository dependency.
    *
    * @param userRepository the repository used to retrieve and save {@link IGRPUserEntity} entities
    */
   public RemoveRolesFromUserCommandHandler(
           IGRPUserEntityRepository userRepository) {
      this.userRepository = userRepository;
   }

   /**
    * Handles the removal of one or more roles from a user.
    *
    * @param command the command containing the user ID and the list of role IDs to remove
    * @return a {@link ResponseEntity} containing the updated list of the user's {@link RoleDTO}s
    * @throws IgrpResponseStatusException if no user is found with the given ID
    */
   @IgrpCommandHandler
   public ResponseEntity<List<RoleDTO>> handle(RemoveRolesFromUserCommand command) {
      String username = command.getUsername();
      List<Integer> roleIdsToRemove = command.getRemoveRolesFromUserRequest();

      logger.info("Attempting to remove roles {} from username={}", roleIdsToRemove, username);

      IGRPUserEntity user = userRepository.findByUsername(command.getUsername())
              .orElseThrow(() -> {
                 logger.warn("User not found with username={}", username);
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND,
                         "Invalid Username",
                         "User not found with username: " + username);
              });

      if (roleIdsToRemove != null && !roleIdsToRemove.isEmpty()) {
         List<RoleEntity> roles = new ArrayList<>(user.getRoles());
         boolean isRolesRemoved = roles.removeIf(role -> roleIdsToRemove.contains(role.getId()));
         if (isRolesRemoved) {
            logger.info("Roles removed successfully from user name={}", username);
         } else {
            logger.info("No matching roles found to remove for user name={}", username);
         }
         user.setRoles(roles);
      } else {
         logger.info("No roles provided for removal for user name={}", username);
      }

      userRepository.save(user);

      List<RoleDTO> result = user.getRoles().stream()
              .map(role -> new RoleDTO(role.getId(),
                      role.getName(), role.getDescription(),
                      null, null,
                      null))
              .collect(Collectors.toList());

      logger.info("Returning {} remaining roles for user name={}", result.size(), username);

      return ResponseEntity.ok(result);
   }

}