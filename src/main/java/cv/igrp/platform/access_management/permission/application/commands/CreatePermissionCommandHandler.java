package cv.igrp.platform.access_management.permission.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.permission.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.permission.domain.service.PermissionValidator;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.validation.ResourceValidationResponse;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import org.springframework.transaction.annotation.Transactional;

/**
 * Command handler responsible for creating a new {@link PermissionEntity} entity in the system.
 *
 * <p>This handler executes the following workflow:</p>
 * <ul>
 *   <li>Receives a {@link CreatePermissionCommand} containing permission creation data.</li>
 *   <li>Validates that the referenced {@link ApplicationEntity} exists.</li>
 *   <li>Maps the incoming {@link PermissionDTO} to a {@link PermissionEntity} entity via {@link PermissionMapper}.</li>
 *   <li>Saves the new permission entity and returns the mapped {@link PermissionDTO}.</li>
 * </ul>
 *
 * <p>If the application ID is invalid (i.e., not found), an {@link IgrpResponseStatusException} is thrown with a {@link HttpStatus#NOT_FOUND} status.</p>
 *
 * @see CreatePermissionCommand
 * @see PermissionEntityRepository
 * @see ApplicationEntityRepository
 * @see PermissionMapper
 * @see PermissionDTO
 * @see Status
 * @see IgrpResponseStatusException
 */
@Slf4j
@Component
public class CreatePermissionCommandHandler implements CommandHandler<CreatePermissionCommand, ResponseEntity<PermissionDTO>> {

   private static final Logger LOGGER = LoggerFactory.getLogger(CreatePermissionCommandHandler.class);

   private final PermissionEntityRepository repository;
   private final ApplicationEntityRepository applicationRepository;
   private final PermissionMapper permissionMapper;

   /**
    * Constructs a new {@code CreatePermissionCommandHandler} with the required dependencies.
    *
    * @param repository            the permission repository used to persist the permission
    * @param applicationRepository the application repository used to validate the application's existence
    * @param permissionMapper      the mapper used to convert entities to DTOs
    */
   public CreatePermissionCommandHandler(PermissionEntityRepository repository, ApplicationEntityRepository applicationRepository, PermissionMapper permissionMapper) {
      this.repository = repository;
      this.applicationRepository = applicationRepository;
      this.permissionMapper = permissionMapper;
   }

   /**
    * Handles the creation of a new permission.
    * <ul>
    *     <li>Validates the application associated with the permission.</li>
    *     <li>Sets default status to {@link Status#ACTIVE} if none provided.</li>
    *     <li>Trims and sets the description if present.</li>
    *     <li>Saves the permission and maps it to a DTO.</li>
    * </ul>
    *
    * @param command the {@link CreatePermissionCommand} containing the permission data
    * @return a {@link ResponseEntity} with status {@code 201 CREATED} and the created {@link PermissionDTO}
    * @throws IgrpResponseStatusException if the application is not found
    */
   @IgrpCommandHandler
   @Transactional
   public ResponseEntity<PermissionDTO> handle(CreatePermissionCommand command) {
      log.info("Create permission with name: {}", command.getPermissiondto().getName());
      PermissionDTO request = command.getPermissiondto();
      ApplicationEntity foundApplication = applicationRepository.findById(command.getPermissiondto().getApplicationId())
              .orElseThrow(() -> {
                 log.warn("Application with id {} not found.", command.getPermissiondto().getApplicationId());
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND, "Create Permission", "Application with id: " + command.getPermissiondto().getApplicationId() + " not found."
                 );
              });
      ResourceValidationResponse validationResponse = PermissionValidator.validatePermissionName(command.getPermissiondto(), foundApplication);
      if (!validationResponse.isValid()) {
         log.warn("Invalid Permission Dto with name {}.", command.getPermissiondto().getName());
         throw IgrpResponseStatusException.of(
                 HttpStatus.CONFLICT, "Create Permission", validationResponse.getFailureMessage());
      }
      PermissionEntity newPermission = permissionMapper.mapDtoToEntity(request, foundApplication);
      PermissionEntity savedPermission = repository.save(newPermission);
      PermissionDTO permissionDTO = permissionMapper.mapToDTO(savedPermission);
      log.info("Permission with name: {} created successfully.", command.getPermissiondto().getName());
      return new ResponseEntity<>(permissionDTO, HttpStatus.CREATED);
   }

}