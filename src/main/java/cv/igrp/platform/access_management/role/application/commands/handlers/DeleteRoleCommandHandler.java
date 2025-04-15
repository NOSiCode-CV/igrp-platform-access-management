package cv.igrp.platform.access_management.role.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Role;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.RoleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.role.application.commands.commands.DeleteRoleCommand;



@Service
public class DeleteRoleCommandHandler implements CommandHandler<DeleteRoleCommand, ResponseEntity<Boolean>> {

   private final RoleRepository repository;
   public DeleteRoleCommandHandler(RoleRepository repository) {

       this.repository = repository;
   }

   @IgrpCommandHandler
   public ResponseEntity<Boolean> handle(DeleteRoleCommand command) {
      Role role = repository.findById(command.getId())
              .orElseThrow(() -> new IgrpResponseStatusException(
                      new IgrpProblem<>(HttpStatus.NOT_FOUND, "Delete Role", "Role with id: " + command.getId() + " not found.")
              ));
      role.setStatus(Status.DELETED);
      repository.save(role);
      return new ResponseEntity<>(true, HttpStatus.NO_CONTENT);
   }

}