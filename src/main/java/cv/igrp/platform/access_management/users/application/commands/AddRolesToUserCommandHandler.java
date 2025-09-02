package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.auth.core.adapter.IAdapter;
import cv.igrp.framework.auth.core.exception.IAMException;
import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
      String userName = command.getUsername();
      List<RoleEntity> roles = new ArrayList<>();

      IGRPUserEntity user = userRepository.findByUsername(userName)
              .orElseThrow(() -> {
                 logger.warn("User not found with name={}", userName);
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND,"Invalid User name",
                         "User not found with name: " + userName);
              });

      for(String roleName : command.getAddRolesToUserRequest()) {

         logger.info("Assigning role name={} to user name={}", roleName, userName);

         RoleEntity roleToAdd = roleRepository.findByNameAndStatusNot(roleName, Status.DELETED)
                 .orElseThrow(() -> {
                    logger.warn("Role not found with name={}", roleName);
                    return IgrpResponseStatusException.of(
                            HttpStatus.NOT_FOUND, "Invalid Role name",
                            "Role not found with name: %s".formatted(roleName));
                 });

         Set<IGRPUserEntity> users = roleToAdd.getUsers();

         if (users == null) {
            users = new HashSet<>();
            roleToAdd.setUsers(users);
         }
         boolean isRoleAdded = users.add(user);

         if (isRoleAdded) {
            logger.info("User name={} successfully added to role name={}", userName, roleName);
            roles.add(roleRepository.save(roleToAdd));
         } else {
            logger.info("User name={} was already associated with role name={}", userName, roleName);
         }
      }
      if(!roles.isEmpty()) {
         for (RoleEntity role : roles) {
            try {
               adapter.assignRoleToUser(role.getDepartment().getCode(),role.getName(),userName);
               logger.info("Role name={} from department with code {} assigned to user name={} in Keycloak",
                       role.getName(),
                       role.getDepartment().getCode(),
                       userName);
            } catch (IAMException e) {
               logger.error("Failed to assign role name={} from {} department to user name={} in Keycloak: {}",
                       role.getName(),
                       role.getDepartment().getCode(),
                       userName,
                       e.getMessage(), e);
               throw new RuntimeException(e);
            }
         }
      }

      List<RoleDTO> rolesDTO = roles.stream().map(roleMapper::mapToDto).toList();

      return ResponseEntity.status(HttpStatus.CREATED).body(rolesDTO);

   }

}