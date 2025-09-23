package cv.igrp.platform.access_management.app.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.app.domain.service.ApplicationValidator;
import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;


/**
 * Command handler responsible for creating a new {@link ApplicationEntity} entity.
 *
 * <p>
 * This handler receives a {@link CreateApplicationCommand}, maps its {@link ApplicationDTO} payload
 * into a new {@link ApplicationEntity} entity, assigns a default {@link Status#ACTIVE}, and persists it to the database.
 * The resulting entity is then converted back into a DTO and returned in the response.
 * </p>
 *
 * <p>
 * The application ID is explicitly set to {@code null} to ensure a new record is created.
 * </p>
 *
 * @see CreateApplicationCommand
 * @see ApplicationDTO
 * @see ApplicationEntity
 * @see ApplicationMapper
 * @see ApplicationEntityRepository
 */
@Component
public class CreateApplicationCommandHandler implements CommandHandler<CreateApplicationCommand, ResponseEntity<ApplicationDTO>> {

   private final ApplicationEntityRepository applicationRepository;
   private final ApplicationMapper applicationMapper;
   private final ApplicationValidator applicationValidator;

   /**
    * Constructs the handler with the required dependencies.
    *
    * @param applicationRepository the repository used to persist the application entity
    * @param applicationMapper     the mapper used to convert between {@link ApplicationEntity} and {@link ApplicationDTO}
    */
   public CreateApplicationCommandHandler(ApplicationEntityRepository applicationRepository, ApplicationMapper applicationMapper, ApplicationValidator applicationValidator) {
      this.applicationRepository = applicationRepository;
      this.applicationMapper = applicationMapper;
      this.applicationValidator = applicationValidator;
   }

   /**
    * Handles the creation of a new application.
    *
    * <ul>
    *   <li>Maps the DTO from the command to a new {@link ApplicationEntity} entity.</li>
    *   <li>Sets the entity's ID to {@code null} to enforce insertion.</li>
    *   <li>Sets the default status to {@link Status#ACTIVE}.</li>
    *   <li>Saves the entity and maps it back to a DTO.</li>
    * </ul>
    *
    * @param command the {@link CreateApplicationCommand} containing the new application data
    * @return a {@link ResponseEntity} with status {@code 201 Created} and the persisted {@link ApplicationDTO}
    */
   @IgrpCommandHandler
   public ResponseEntity<ApplicationDTO> handle(CreateApplicationCommand command) {
      var validation = applicationValidator.validateApplicationCode(command.getApplicationdto());
      if(!validation.isValid()) {
         throw IgrpResponseStatusException.of(
                 HttpStatus.CONFLICT, "Create Application", validation.getFailureMessage()
         );
      }
      ApplicationEntity application = applicationMapper.toEntity(command.getApplicationdto());
      application.setId(null);
      ApplicationEntity savedApplication = applicationRepository.save(application);
      ApplicationDTO applicationDTO =  applicationMapper.toDto(savedApplication);
      return ResponseEntity.status(HttpStatus.CREATED).body(applicationDTO);
   }

}