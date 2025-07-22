package cv.igrp.platform.access_management.app.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Command handler responsible for logically deleting an {@link ApplicationEntity}.
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
 * @see ApplicationEntity
 * @see ApplicationEntityRepository
 * @see Status
 * @see IgrpResponseStatusException
 */
@Component
public class DeleteApplicationCommandHandler implements CommandHandler<DeleteApplicationCommand, ResponseEntity<String>> {

   private ApplicationEntityRepository applicationRepository;

   /**
    * Constructs the handler with the required repository dependency.
    *
    * @param applicationRepository the repository used to fetch and persist {@link ApplicationEntity} entities
    */
   public DeleteApplicationCommandHandler(ApplicationEntityRepository applicationRepository) {
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
      ApplicationEntity application = applicationRepository.findByCode(command.getCode())
              .orElseThrow(() -> IgrpResponseStatusException.of(HttpStatus.NOT_FOUND, "Application not found", "Application not found with code: " + command.getCode()));

      application.setStatus(Status.DELETED);
      applicationRepository.save(application);
      return ResponseEntity.noContent().build();
   }

}