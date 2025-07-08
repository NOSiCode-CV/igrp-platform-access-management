package cv.igrp.platform.access_management.permission.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Permission;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.PermissionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.permission.application.commands.commands.DeletePermissionCommand;
import org.springframework.transaction.annotation.Transactional;

/**
 * Command handler responsible for performing a logical deletion of a {@link Permission}.
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
 * The updated {@link Permission} is persisted using {@link PermissionRepository}, and the result is returned
 * as a {@link ResponseEntity} with status {@code 204 NO_CONTENT}.
 * </p>
 *
 * @see DeletePermissionCommand
 * @see Permission
 * @see PermissionRepository
 * @see Status
 * @see IgrpResponseStatusException
 * @see ResponseEntity
 */
@Slf4j
@Service
public class DeletePermissionCommandHandler implements CommandHandler<DeletePermissionCommand, ResponseEntity<Boolean>> {

    /**
     * Constructs the handler with the required repository.
     *
     * @param permissionRepository the repository used to access and update permissions
     */
   private final PermissionRepository permissionRepository;
   public DeletePermissionCommandHandler(PermissionRepository permissionRepository) {

       this.permissionRepository = permissionRepository;
   }

    /**
     * Handles the deletion of a {@link Permission} by setting its status to {@link Status#DELETED}.
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
      log.info("Delete permission with id: {}", command.getId());
      Permission permission = permissionRepository.findById(command.getId())
              .orElseThrow(() -> {
                 log.warn("Permission with id {} not found", command.getId());
                 return IgrpResponseStatusException.of(
                        HttpStatus.NOT_FOUND, "Delete Permission", "Permission with id: " + command.getId() + " not found."
                 );
              });
      permission.setStatus(Status.DELETED);
      permissionRepository.save(permission);
      log.info("Permission with id: {} deleted successfully", command.getId());
      return new ResponseEntity<>(true, HttpStatus.NO_CONTENT);
   }

}