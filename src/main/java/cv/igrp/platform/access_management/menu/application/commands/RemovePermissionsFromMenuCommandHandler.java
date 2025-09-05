package cv.igrp.platform.access_management.menu.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.permission.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.role.application.commands.RemovePermissionsCommand;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import org.springframework.transaction.annotation.Transactional;

/**
 * Command handler responsible for removing a list of permissions from a specific menu entry.
 * <p>
 * This handler:
 * <ul>
 *     <li>Fetches the menu entry by its code, ensuring it is not marked as {@link Status#DELETED}</li>
 *     <li>Iterates over the list of permission names to remove</li>
 *     <li>If the permission exists in the menu entry, it is removed and mapped to a {@link PermissionDTO}</li>
 *     <li>Saves the updated menu entry</li>
 * </ul>
 * The result is a list of {@link PermissionDTO}s that were successfully removed from the menu entry.
 * @see RemovePermissionsFromMenuCommand
 * @see MenuEntryEntity
 * @see PermissionEntity
 * @see MenuEntryEntityRepository
 * @see PermissionMapper
 * @see PermissionDTO
 * @see Status
 * @see IgrpResponseStatusException
 *
 */
@Slf4j
@Component
public class RemovePermissionsFromMenuCommandHandler implements CommandHandler<RemovePermissionsFromMenuCommand, ResponseEntity<List<PermissionDTO>>> {

   private final MenuEntryEntityRepository menuEntryRepository;
   private final PermissionMapper permissionMapper;

   /**
    * Constructs a new instance of {@code RemovePermissionsFromMenuCommandHandler} with the necessary dependencies.
    *
    * @param menuEntryRepository    the repository used to retrieve and persist menu entry entities
    * @param permissionMapper  mapper used to convert {@link PermissionEntity} entities into {@link PermissionDTO}
    */
   public RemovePermissionsFromMenuCommandHandler(
           MenuEntryEntityRepository menuEntryRepository,
           PermissionMapper permissionMapper
   ) {
      this.menuEntryRepository = menuEntryRepository;
      this.permissionMapper = permissionMapper;
   }

   /**
    * Handles the removal of permissions from a menu entry.
    * <p>
    * For each permission name provided in the {@link RemovePermissionsCommand}, the method checks whether
    * the permission is currently associated with the menu entry. If so, it is removed from the menu entry and included
    * in the response.
    *
    * @param command the command containing the menu entry code and a list of permission names to remove
    * @return a {@link ResponseEntity} with the list of removed permissions as {@link PermissionDTO}s and HTTP status {@code 200 OK}
    * @throws IgrpResponseStatusException if the menu entry does not exist or is marked as {@link Status#DELETED}
    */
   @IgrpCommandHandler
   @Transactional
   public ResponseEntity<List<PermissionDTO>> handle(RemovePermissionsFromMenuCommand command) {

      log.info("Remove Permissions with name: {} from menu entry with code: {}.", command.getRemovePermissionsFromMenuRequest().stream().toList(), command.getCode());
      List<PermissionDTO> response = new ArrayList<>();

      MenuEntryEntity foundMenu = menuEntryRepository.findByCodeAndStatusNot(command.getCode(), Status.DELETED)
              .orElseThrow(() -> {
                 log.warn("Menu Entry with code: {} not found.", command.getCode());
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND, "Remove Permission By Menu Entry code", "Menu Entry with code: " + command.getCode() + " not found."
                 );
              });

      for (String permissionId : command.getRemovePermissionsFromMenuRequest()) {
         foundMenu.getPermissions()
                 .stream()
                 .filter(permission -> permission.getName().equals(permissionId))
                 .findFirst()
                 .ifPresent(permission -> {
                    foundMenu.getPermissions().remove(permission);
                    response.add(permissionMapper.mapToDTO(permission));
                 });
      }
      log.info("Permissions with IDs {} removed from Role with name: {} successfully.", command.getRemovePermissionsFromMenuRequest().stream().toList(), command.getCode());
      menuEntryRepository.save(foundMenu);
      return new ResponseEntity<>(response, HttpStatus.OK);

   }

}