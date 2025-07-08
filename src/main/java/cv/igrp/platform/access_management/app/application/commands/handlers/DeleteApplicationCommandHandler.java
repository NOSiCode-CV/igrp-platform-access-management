package cv.igrp.platform.access_management.app.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ApplicationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.app.application.commands.commands.DeleteApplicationCommand;


/**
 * Command handler responsible for logically deleting an {@link Application}.
 *
 * <p>
 * This handler receives a {@link DeleteApplicationCommand}, verifies the existence of the application
 * by its ID, and performs a soft delete by setting its {@link Status} to {@link Status#DELETED}.
 * </p>
 *
 * <p>
 * If the application is not found, an {@link IgrpResponseStatusException} is thrown with HTTP status {@code 404 NOT_FOUND}.
 * </p>
 *
 * @see DeleteApplicationCommand
 * @see Application
 * @see ApplicationRepository
 * @see Status
 * @see IgrpResponseStatusException
 */
@Service
public class DeleteApplicationCommandHandler implements CommandHandler<DeleteApplicationCommand, ResponseEntity<String>> {

   private ApplicationRepository applicationRepository;

   /**
    * Constructs the handler with the required repository dependency.
    *
    * @param applicationRepository the repository used to fetch and persist {@link Application} entities
    */
   public DeleteApplicationCommandHandler(ApplicationRepository applicationRepository) {
      this.applicationRepository = applicationRepository;
   }

   /**
    * Handles the soft deletion of an application by ID.
    *
    * <ul>
    *   <li>Fetches the application from the repository.</li>
    *   <li>If found, sets its status to {@link Status#DELETED} and saves it.</li>
    *   <li>If not found, throws {@link IgrpResponseStatusException}.</li>
    * </ul>
    *
    * @param command the command containing the ID of the application to delete
    * @return a {@link ResponseEntity} with HTTP {@code 204 NO_CONTENT} on success
    */
   @IgrpCommandHandler
   public ResponseEntity<String> handle(DeleteApplicationCommand command) {
      Application application = applicationRepository.findById(command.getId())
              .orElseThrow(() -> IgrpResponseStatusException.of(HttpStatus.NOT_FOUND, "Application not found", "Application not found with id: " + command.getId()));

      application.setStatus(Status.DELETED);
      applicationRepository.save(application);
      return ResponseEntity.noContent().build();
   }

}