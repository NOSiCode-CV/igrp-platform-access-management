package cv.igrp.platform.access_management.role.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.permission.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
 * The result is a list of {@link PermissionDTO}s that were successfully removed from the role.
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
public class RemovePermissionsCommandHandler implements CommandHandler<RemovePermissionsCommand, ResponseEntity<List<PermissionDTO>>> {

   private final RoleEntityRepository roleRepository;
   private final PermissionMapper permissionMapper;

   /**
    * Constructs a new instance of {@code RemovePermissionsCommandHandler} with the necessary dependencies.
    *
    * @param roleRepository    the repository used to retrieve and persist role entities
    * @param permissionMapper  mapper used to convert {@link PermissionEntity} entities into {@link PermissionDTO}
    */
   public RemovePermissionsCommandHandler(RoleEntityRepository roleRepository, PermissionMapper permissionMapper) {
      this.roleRepository = roleRepository;
      this.permissionMapper = permissionMapper;
   }

   /**
    * Handles the removal of permissions from a role.
    * <p>
    * For each permission ID provided in the {@link RemovePermissionsCommand}, the method checks whether
    * the permission is currently associated with the role. If so, it is removed from the role and included
    * in the response.
    *
    * @param command the command containing the role ID and a list of permission IDs to remove
    * @return a {@link ResponseEntity} with the list of removed permissions as {@link PermissionDTO}s and HTTP status {@code 200 OK}
    * @throws IgrpResponseStatusException if the role does not exist or is marked as {@link Status#DELETED}
    */
   @IgrpCommandHandler
   @Transactional
   public ResponseEntity<List<PermissionDTO>> handle(RemovePermissionsCommand command) {
      log.info("Remove Permissions with name: {} from Role with name: {}.", command.getRemovePermissionsRequest().stream().toList(), command.getName());
      List<PermissionDTO> response = new ArrayList<>();
      RoleEntity foundRole = roleRepository.findByNameAndStatusNot(command.getName(), Status.DELETED)
              .orElseThrow(() -> {
                 log.warn("Role with name: {} not found.", command.getName());
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND, "Remove Permission By Role ID", "Role with id: " + command.getName() + " not found."
                 );
              });
      for (String permissionId : command.getRemovePermissionsRequest()) {
         foundRole.getPermissions()
                 .stream()
                 .filter(permission -> permission.getName().equals(permissionId))
                 .findFirst()
                 .ifPresent(permission -> {
                    foundRole.getPermissions().remove(permission);
                    response.add(permissionMapper.mapToDTO(permission));
                 });
      }
      log.info("Permissions with IDs {} removed from Role with name: {} successfully.", command.getRemovePermissionsRequest().stream().toList(), command.getName());
      roleRepository.save(foundRole);
      return new ResponseEntity<>(response, HttpStatus.OK);
   }

}