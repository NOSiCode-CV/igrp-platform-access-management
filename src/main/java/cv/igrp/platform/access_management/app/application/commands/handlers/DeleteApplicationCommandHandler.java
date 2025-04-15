package cv.igrp.platform.access_management.app.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ApplicationRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.app.application.commands.commands.DeleteApplicationCommand;



@Service
public class DeleteApplicationCommandHandler implements CommandHandler<DeleteApplicationCommand, ResponseEntity<String>> {

   private ApplicationRepository applicationRepository;

   public DeleteApplicationCommandHandler(ApplicationRepository applicationRepository) {
      this.applicationRepository = applicationRepository;
   }

   @IgrpCommandHandler
   public ResponseEntity<String> handle(DeleteApplicationCommand command) {
      Application application = applicationRepository.findById(command.getId())
              .orElseThrow(() -> new EntityNotFoundException("Application not found with id: " + command.getId()));
      application.setStatus(Status.DELETED);
      applicationRepository.save(application);
      return ResponseEntity.noContent().build();
   }

}