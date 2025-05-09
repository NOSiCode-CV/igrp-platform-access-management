package cv.igrp.platform.access_management.app.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.app.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ApplicationRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.app.application.commands.commands.UpdateApplicationCommand;


/**
 * Command handler responsible for updating an existing {@link Application} entity.
 * <p>
 * This handler receives an {@link UpdateApplicationCommand}, retrieves the target application
 * by ID, updates its fields with the provided data, persists the changes, and returns the updated
 * application as a {@link ApplicationDTO}.
 * </p>
 *
 * <p>
 * If no application is found for the given ID, an {@link IgrpResponseStatusException} is thrown with
 * HTTP status {@code 404 NOT_FOUND}.
 * </p>
 *
 * @see UpdateApplicationCommand
 * @see ApplicationRepository
 * @see ApplicationMapper
 * @see ApplicationDTO
 */
@Service
public class UpdateApplicationCommandHandler implements CommandHandler<UpdateApplicationCommand, ResponseEntity<ApplicationDTO>> {

   private ApplicationRepository applicationRepository;
   private ApplicationMapper applicationMapper;

   /**
    * Constructs a new {@code UpdateApplicationCommandHandler} with the required dependencies.
    *
    * @param applicationRepository the repository used to retrieve and persist {@link Application} entities
    * @param applicationMapper     the mapper used to convert between {@link Application} and {@link ApplicationDTO}
    */
   public UpdateApplicationCommandHandler(ApplicationRepository applicationRepository, ApplicationMapper applicationMapper) {
      this.applicationRepository = applicationRepository;
      this.applicationMapper = applicationMapper;
   }

   /**
    * Handles the update of an {@link Application} based on the data provided in the {@link UpdateApplicationCommand}.
    * <ul>
    *     <li>Retrieves the application by ID.</li>
    *     <li>Updates the entity's fields with values from the {@link ApplicationDTO}.</li>
    *     <li>Persists the updated entity and returns the result as a DTO.</li>
    * </ul>
    *
    * @param command the command containing the application ID and updated data
    * @return a {@link ResponseEntity} containing the updated {@link ApplicationDTO}
    * @throws IgrpResponseStatusException if the application is not found
    */
   @IgrpCommandHandler
   public ResponseEntity<ApplicationDTO> handle(UpdateApplicationCommand command) {
      Application application = applicationRepository.findById(command.getId())
              .orElseThrow(() -> {
                 return new IgrpResponseStatusException(new IgrpProblem<String>(HttpStatus.NOT_FOUND, "Application not found", "Application not found with id: " + command.getId()));
              });

      ApplicationDTO appDto = command.getApplicationdto();
      application.setCode(appDto.getCode());
      application.setName(appDto.getName());
      application.setDescription(appDto.getDescription());
      application.setStatus(appDto.getStatus());
      application.setType(appDto.getType());
      application.setOwner(appDto.getOwner());
      application.setPicture(appDto.getPicture());
      application.setUrl(appDto.getUrl() != null ? appDto.getUrl().toString() : null);
      application.setSlug(appDto.getSlug());

      Application updatedApplication = applicationRepository.save(application);
      return ResponseEntity.ok(applicationMapper.toDto(updatedApplication));
   }

}