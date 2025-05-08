package cv.igrp.platform.access_management.users.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.domain.models.Role;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.IGRPUserRepository;
import cv.igrp.platform.access_management.shared.domain.models.IGRPUser;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.users.application.commands.commands.RemoveRolesFromUserCommand;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import jakarta.persistence.EntityNotFoundException;

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

   private final IGRPUserRepository userRepository;

   /**
    * Constructs the handler with the required repository dependency.
    *
    * @param userRepository the repository used to retrieve and save {@link IGRPUser} entities
    */
   public RemoveRolesFromUserCommandHandler(IGRPUserRepository userRepository) {
      this.userRepository = userRepository;
   }

   /**
    * Handles the removal of one or more roles from a user.
    *
    * @param command the command containing the user ID and the list of role IDs to remove
    * @return a {@link ResponseEntity} containing the updated list of the user's {@link RoleDTO}s
    * @throws EntityNotFoundException if no user is found with the given ID
    */
   @IgrpCommandHandler
   public ResponseEntity<List<RoleDTO>> handle(RemoveRolesFromUserCommand command) {

      IGRPUser user = userRepository.findById(command.getId())
              .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + command.getId()));

      List<Integer> roleIdsToRemove = command.getRemoveRolesFromUserRequest();
      if (roleIdsToRemove != null && !roleIdsToRemove.isEmpty()) {
         List<Role> roles = new ArrayList<>(user.getRoles());
         roles.removeIf(role -> roleIdsToRemove.contains(role.getId()));
         user.setRoles(roles);
      }

      userRepository.save(user);

      List<RoleDTO> result = user.getRoles().stream()
              .map(role -> new RoleDTO(role.getId(), role.getName(), role.getDescription(), null, null, null))
              .collect(Collectors.toList());

      return ResponseEntity.ok(result);
   }
}
