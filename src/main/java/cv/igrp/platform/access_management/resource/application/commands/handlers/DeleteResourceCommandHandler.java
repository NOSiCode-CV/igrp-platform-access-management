package cv.igrp.platform.access_management.resource.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Resource;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ResourceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.resource.application.commands.commands.DeleteResourceCommand;



@Service
public class DeleteResourceCommandHandler implements CommandHandler<DeleteResourceCommand, ResponseEntity<String>> {

   private ResourceRepository resourceRepository;

   public DeleteResourceCommandHandler(ResourceRepository resourceRepository) {
      this.resourceRepository = resourceRepository;
   }

   @IgrpCommandHandler
   public ResponseEntity<String> handle(DeleteResourceCommand command) {
      Resource resource = resourceRepository.findById(command.getId())
              .orElseThrow(() -> {
                 return new IgrpResponseStatusException(new IgrpProblem<String>(HttpStatus.NOT_FOUND, "Resource not found", "Resource not found with id: " + command.getId()));
              });
      resourceRepository.delete(resource);
      return ResponseEntity.noContent().build();
   }

}