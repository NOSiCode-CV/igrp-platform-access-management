package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.auth.core.adapter.IAdapter;
import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.exceptions.NoActionPerformedException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
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
   private final RoleMapper roleMapper;
   private final IAdapter adapter;

   /**
    * Constructs the handler with required dependencies.
    *
    * @param userRepository the repository used to retrieve user entities
    * @param roleRepository the repository used to retrieve and update roles
    * @param roleMapper the mapper used to convert role entities to DTOs
    * @param adapter the adapter to assign role to user in iam
    */
   public AddRolesToUserCommandHandler(
           IGRPUserEntityRepository userRepository,
           RoleEntityRepository roleRepository,
           RoleMapper roleMapper,
           IAdapter adapter) {
      this.userRepository = userRepository;
      this.roleRepository = roleRepository;
      this.roleMapper = roleMapper;
      this.adapter = adapter;
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
      String username = command.getUsername();
      List<String> rolesToAdd = command.getAddRolesToUserRequest();

      if (rolesToAdd.isEmpty())
         throw new NoActionPerformedException("No action performed because the role list is empty");

      List<RoleEntity> successfullyAssignedInKeycloak = new ArrayList<>();

      IGRPUserEntity user = userRepository.findByUsername(username)
              .orElseThrow(() -> {
                 logger.warn("User not found with name={}", username);
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND,"Invalid User name",
                         "User not found with name: %s".formatted(username));
              });

      List<RoleEntity> roles = user.getRoles();

      try {
         Set<String> existingRoleCodes = roles.stream()
                 .filter(role -> !Objects.equals(role.getStatus(),Status.DELETED))
                 .map(RoleEntity::getName)
                 .collect(Collectors.toSet());

         for (String role : command.getAddRolesToUserRequest()) {

            if (existingRoleCodes.contains(role)) {
               continue;
            }

            logger.info("Assigning role name={} to user name={}", role, username);
            RoleEntity roleEntity = roleRepository.findByCodeAndStatusNot(role, Status.DELETED)
                    .orElseThrow(() -> {
                       logger.warn("Role not found with name={}", role);
                       return IgrpResponseStatusException.of(
                               HttpStatus.NOT_FOUND, "Invalid Role name",
                               "Role not found with name: %s".formatted(role));
                    });
            if(roleEntity.getUsers()==null) {
               roleEntity.setUsers(new HashSet<>());
            }
            roleEntity.getUsers().add(user);
            roleRepository.save(roleEntity);
//            user.getRoles().add(roleEntity);

            adapter.assignRoleToUser(roleEntity.getDepartment().getCode(), roleEntity.getName(), command.getUsername());
            successfullyAssignedInKeycloak.add(roleEntity);
         }
         //@ManyToMany(mappedBy = "users", fetch = FetchType.LAZY)
         //Não funciona : o lado "owner" da relação está em RoleEntity e não no IGRPUserEntity
//         userRepository.save(user);
      } catch (Exception e) {
         logger.error("Error while adding roles into Keycloak for username={}. Starting compensation...", username, e);

         for (RoleEntity role : successfullyAssignedInKeycloak) {
            try {
               adapter.unassignRoleFromUser(role.getDepartment().getCode(), role.getName(), command.getUsername());
            } catch (Exception rollbackEx) {
               logger.error("Compensation failed: could not revert role={} in Keycloak for user={}: {}",
                       role.getName(),
                       command.getUsername(),
                       rollbackEx.getMessage());
            }
         }
         throw IgrpResponseStatusException.of(
                 HttpStatus.INTERNAL_SERVER_ERROR,
                 "Add Roles to User Failed",
                 e.getMessage()
         );
      }

      // Showing only the new ones.
      List<RoleDTO> rolesDTO = successfullyAssignedInKeycloak.stream().map(roleMapper::mapToDto).toList();

      return ResponseEntity.status(HttpStatus.CREATED).body(rolesDTO);

   }

}