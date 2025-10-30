package cv.igrp.platform.access_management.role.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.permission.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Command handler responsible for removing a list of permissions from a specific role.
 * <p>
 * This handler:
 * <ul>
 *     <li>Fetches the role by its ID, ensuring it is not marked as {@link Status#DELETED}</li>
 *     <li>Iterates over the list of permission IDs to remove</li>
 *     <li>If the permission exists in the role, it is removed and mapped to a {@link PermissionDTO}</li>
 *     <li>Saves the updated role</li>
 * </ul>
 * The result is an updated {@link RoleDTO} without the permissions successfully removed from the role.
 * @see RemovePermissionsCommand
 * @see RoleEntity
 * @see PermissionEntity
 * @see RoleEntityRepository
 * @see PermissionMapper
 * @see PermissionDTO
 * @see Status
 * @see IgrpResponseStatusException
 *
 */
@Slf4j
@Component
public class RemovePermissionsCommandHandler implements CommandHandler<RemovePermissionsCommand, ResponseEntity<RoleDTO>> {

   private final RoleEntityRepository roleRepository;
   private final RoleMapper roleMapper;

   /**
    * Constructs a new instance of {@code RemovePermissionsCommandHandler} with the necessary dependencies.
    *
    * @param roleRepository    the repository used to retrieve and persist role entities
    * @param roleMapper        mapper used to convert {@link RoleEntity} entities into {@link RoleDTO}
    */
   public RemovePermissionsCommandHandler(RoleEntityRepository roleRepository, RoleMapper roleMapper) {
      this.roleRepository = roleRepository;
       this.roleMapper = roleMapper;
   }

   /**
    * Handles the removal of permissions from a role.
    * <p>
    * For each permission ID provided in the {@link RemovePermissionsCommand}, the method checks whether
    * the permission is currently associated with the role. If so, it is removed from the role and included
    * in the response.
    *
    * @param command the command containing the role ID and a list of permission IDs to remove
    * @return a {@link ResponseEntity} with the updated role and HTTP status {@code 200 OK}
    * @throws IgrpResponseStatusException if the role does not exist or is marked as {@link Status#DELETED}
    */
   @IgrpCommandHandler
   @Transactional
   public ResponseEntity<RoleDTO> handle(RemovePermissionsCommand command) {
      log.info("Remove Permissions with name: {} from Role with code: {}.", command.getRemovePermissionsRequest().stream().toList(), command.getCode());
      RoleEntity foundRole = roleRepository.findByCodeAndStatusNot(command.getCode(), Status.DELETED)
              .orElseThrow(() -> {
                 log.warn("Role with code: {} not found.", command.getCode());
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND, "Remove Permission By Role ID", "Role with code: " + command.getCode() + " not found."
                 );
              });
      for (String permissionId : command.getRemovePermissionsRequest()) {
         foundRole.getPermissions()
                 .stream()
                 .filter(permission -> permission.getName().equals(permissionId))
                 .findFirst()
                 .ifPresent(permission -> foundRole.getPermissions().remove(permission));
      }
      log.info("Permissions with IDs {} removed from Role with code: {} successfully.", command.getRemovePermissionsRequest(), command.getCode());
      removePermissionsForChildren(foundRole, command.getRemovePermissionsRequest());
      var response = roleMapper.mapToDto(roleRepository.save(foundRole));
      return new ResponseEntity<>(response, HttpStatus.OK);
   }

   private void removePermissionsForChildren(RoleEntity role, List<String> permissionNames) {

       if(!role.getChildren().isEmpty()) {

           for (var child : role.getChildren()) {

               var childRole = roleRepository.findByCodeAndStatusNotDeleted(child.getCode());

               for (var permissionName : permissionNames) {

                   childRole.getPermissions()
                           .stream()
                           .filter(permission -> permission.getName().equals(permissionName))
                           .findFirst()
                           .ifPresent(permission -> childRole.getPermissions().remove(permission));

               }

               roleRepository.save(childRole);

               removePermissionsForChildren(childRole, permissionNames);

               log.info("Permissions with IDs {} removed from child role with code: {} successfully.", permissionNames, childRole.getCode());

           }

       }

   }

}