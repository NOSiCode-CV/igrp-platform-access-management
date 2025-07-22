package cv.igrp.platform.access_management.permission.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


/**
 * Command handler responsible for performing a logical deletion of a {@link PermissionEntity}.
 *
 * <p>
 * Instead of physically removing the permission from the database, this handler sets its {@link Status}
 * to {@link Status#DELETED}, representing a soft delete strategy.
 * </p>
 *
 * <p>
 * If the permission with the specified ID does not exist, an {@link IgrpResponseStatusException} is thrown
 * with an appropriate {@link HttpStatus#NOT_FOUND} response.
 * </p>
 *
 * <p>
 * The updated {@link PermissionEntity} is persisted using {@link PermissionEntityRepository}, and the result is returned
 * as a {@link ResponseEntity} with status {@code 204 NO_CONTENT}.
 * </p>
 *
 * @see DeletePermissionCommand
 * @see PermissionEntity
 * @see PermissionEntityRepository
 * @see Status
 * @see IgrpResponseStatusException
 * @see ResponseEntity
 */
@Slf4j
@Component
public class DeletePermissionCommandHandler implements CommandHandler<DeletePermissionCommand, ResponseEntity<Boolean>> {

   /**
    * Constructs the handler with the required repository.
    *
    * @param permissionRepository the repository used to access and update permissions
    */
   private final PermissionEntityRepository permissionRepository;
   public DeletePermissionCommandHandler(PermissionEntityRepository permissionRepository) {

      this.permissionRepository = permissionRepository;
   }

   /**
    * Handles the deletion of a {@link PermissionEntity} by setting its status to {@link Status#DELETED}.
    * <p>
    * If the permission with the given ID is not found, it throws {@link IgrpResponseStatusException}.
    * </p>
    *
    * @param command the command containing the ID of the permission to delete
    * @return a {@link ResponseEntity} with status {@code 204 NO_CONTENT} if successful
    */
   @IgrpCommandHandler
   @Transactional
   public ResponseEntity<Boolean> handle(DeletePermissionCommand command) {
      log.info("Delete permission with name: {}", command.getName());
      PermissionEntity permission = permissionRepository.findByName(command.getName())
              .orElseThrow(() -> {
                 log.warn("Permission with name {} not found", command.getName());
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND, "Delete Permission", "Permission with name: " + command.getName() + " not found."
                 );
              });
      permission.setStatus(Status.DELETED);
      permissionRepository.save(permission);
      log.info("Permission with name: {} deleted successfully", command.getName());
      return new ResponseEntity<>(true, HttpStatus.NO_CONTENT);
   }

}