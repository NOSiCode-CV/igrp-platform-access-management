package cv.igrp.platform.access_management.users.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Role;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.IGRPUserRepository;
import cv.igrp.platform.access_management.shared.domain.models.IGRPUser;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.users.application.commands.commands.RemoveRolesFromUserCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command handler responsible for removing one or more {@link Role} instances from a specific {@link IGRPUser}.
 * <p>
 * This handler processes the {@link RemoveRolesFromUserCommand}, which contains a user ID and a list of role IDs to remove.
 * It performs the following steps:
 * <ul>
 *   <li>Fetches the user from the {@link IGRPUserRepository} using the provided user ID.</li>
 *   <li>If the list of role IDs is non-null and non-empty, it removes any matching roles from the user’s role list.</li>
 *   <li>Saves the updated user entity back to the repository.</li>
 *   <li>Converts the user’s updated role list to a list of {@link RoleDTO} and returns it in an HTTP 200 OK response.</li>
 * </ul>
 *
 * <p>This handler also performs a defensive copy of the user’s role list to ensure mutability and prevent runtime exceptions.</p>
 *
 * @see RemoveRolesFromUserCommand
 * @see IGRPUserRepository
 * @see Role
 * @see RoleDTO
 */
@Service
public class RemoveRolesFromUserCommandHandler implements CommandHandler<RemoveRolesFromUserCommand, ResponseEntity<List<RoleDTO>>> {

   private static final Logger logger =
           LoggerFactory.getLogger(RemoveRolesFromUserCommandHandler.class);

   private final IGRPUserRepository userRepository;

   /**
    * Constructs the handler with the required repository dependency.
    *
    * @param userRepository the repository used to retrieve and save {@link IGRPUser} entities
    */
   public RemoveRolesFromUserCommandHandler(
           IGRPUserRepository userRepository) {
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
      Integer userId = command.getId();
      List<Integer> roleIdsToRemove = command.getRemoveRolesFromUserRequest();

      logger.info("Attempting to remove roles {} from user id={}", roleIdsToRemove, userId);

      IGRPUser user = userRepository.findById(command.getId())
              .orElseThrow(() -> {
                 logger.warn("User not found with id={}", userId);
                 return new IgrpResponseStatusException(
                         new IgrpProblem<>(HttpStatus.NOT_FOUND,
                                 "Invalid User id",
                                 "User not found with id: " + userId));
                   });

      if (roleIdsToRemove != null && !roleIdsToRemove.isEmpty()) {
         List<Role> roles = new ArrayList<>(user.getRoles());
         boolean isRolesRemoved = roles.removeIf(role -> roleIdsToRemove.contains(role.getId()));
         if (isRolesRemoved) {
            logger.info("Roles removed successfully from user id={}", userId);
         } else {
            logger.info("No matching roles found to remove for user id={}", userId);
         }
         user.setRoles(roles);
      } else {
         logger.info("No roles provided for removal for user id={}", userId);
      }

      userRepository.save(user);

      List<RoleDTO> result = user.getRoles().stream()
              .map(role -> new RoleDTO(role.getId(),
                      role.getName(), role.getDescription(),
                      null, null,
                      null))
              .collect(Collectors.toList());

      logger.info("Returning {} remaining roles for user id={}", result.size(), userId);

      return ResponseEntity.ok(result);
   }
}
