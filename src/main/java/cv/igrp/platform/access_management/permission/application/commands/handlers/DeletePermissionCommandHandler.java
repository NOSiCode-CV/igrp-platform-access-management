package cv.igrp.platform.access_management.permission.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Permission;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.PermissionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.permission.application.commands.commands.DeletePermissionCommand;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
public class DeletePermissionCommandHandler implements CommandHandler<DeletePermissionCommand, ResponseEntity<Boolean>> {

   private final PermissionRepository permissionRepository;
   public DeletePermissionCommandHandler(PermissionRepository permissionRepository) {

       this.permissionRepository = permissionRepository;
   }

   @IgrpCommandHandler
   @Transactional
   public ResponseEntity<Boolean> handle(DeletePermissionCommand command) {
      log.info("Delete permission with id: {}", command.getId());
      Permission permission = permissionRepository.findById(command.getId())
              .orElseThrow(() -> {
                 log.warn("Permission with id {} not found", command.getId());
                 return new IgrpResponseStatusException(
                         new IgrpProblem<>(HttpStatus.NOT_FOUND, "Delete Permission", "Permission with id: " + command.getId() + " not found.")
                 );
              });
      permission.setStatus(Status.DELETED);
      permissionRepository.save(permission);
      log.info("Permission with id: {} deleted successfully", command.getId());
      return new ResponseEntity<>(true, HttpStatus.NO_CONTENT);
   }

}