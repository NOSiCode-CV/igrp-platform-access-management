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

import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import org.springframework.transaction.annotation.Transactional;

import static cv.igrp.platform.access_management.shared.application.constants.Status.DELETED;

/**
 * Handles the update operation for a {@link PermissionEntity}.
 * <p>
 * This command handler is responsible for validating the existence of the permission and associated
 * {@link ApplicationEntity}, updating the permission's data (name, description, application, and status),
 * and returning the updated result as a {@link PermissionDTO}.
 * </p>
 * <p>
 * If the permission or the application does not exist, an {@link IgrpResponseStatusException} with HTTP 404
 * is thrown.
 * </p>
 *
 * @see UpdatePermissionCommand
 * @see PermissionEntityRepository
 * @see ApplicationEntityRepository
 * @see PermissionMapper
 * @see PermissionEntity
 * @see ApplicationEntity
 * @see PermissionDTO
 * @see Status
 * @see IgrpResponseStatusException
 */
@Slf4j
@Component
public class UpdatePermissionCommandHandler implements CommandHandler<UpdatePermissionCommand, ResponseEntity<PermissionDTO>> {

   private final PermissionEntityRepository permissionRepository;
   private final ApplicationEntityRepository applicationRepository;
   private final PermissionMapper permissionMapper;

   /**
    * Constructs the handler with required dependencies.
    *
    * @param permissionRepository     repository to access and persist permission entities
    * @param applicationRepository    repository to retrieve the associated application entity
    * @param permissionMapper         mapper to convert permission entities to DTOs
    */
   public UpdatePermissionCommandHandler(PermissionEntityRepository permissionRepository, ApplicationEntityRepository applicationRepository, PermissionMapper permissionMapper) {
      this.permissionRepository = permissionRepository;
      this.applicationRepository = applicationRepository;
      this.permissionMapper = permissionMapper;
   }


   /**
    * Handles the update of a {@link PermissionEntity} based on the given {@link UpdatePermissionCommand}.
    * <ul>
    *     <li>Validates that the permission exists and is not deleted.</li>
    *     <li>Validates that the referenced application exists.</li>
    *     <li>Updates name, optional description, status and application association.</li>
    *     <li>Returns the updated entity as a {@link PermissionDTO} with status {@code 200 OK}.</li>
    * </ul>
    *
    * @param command the command containing the ID and new data for the permission
    * @return a {@link ResponseEntity} with the updated {@link PermissionDTO}
    * @throws IgrpResponseStatusException if the permission or application is not found
    */
   @IgrpCommandHandler
   @Transactional
   public ResponseEntity<PermissionDTO> handle(UpdatePermissionCommand command) {
      log.info("Update permission with name: {}", command.getPermissiondto().getName());
      PermissionEntity foundPermission = permissionRepository.findByNameAndStatusNot(command.getPermissiondto().getName(), DELETED)
              .orElseThrow(() -> {
                 log.warn("Permission with id: {} not found", command.getId());
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND, "Update Permission", "Permission with name: " + command.getPermissiondto().getName() + " not found."
                 );
              });
      ApplicationEntity application = applicationRepository.findByCode(command.getPermissiondto().getApplicationCode())
              .orElseThrow(() -> {
                 log.warn("Application with code: {} not found", command.getPermissiondto().getApplicationCode());
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND, "Update Permission", "Application with code: " + command.getPermissiondto().getApplicationCode() + " not found."
                 );
              });
      ResourceValidationResponse validationResponse = PermissionValidator.validatePermissionName(command.getPermissiondto(), application);
      if (!validationResponse.isValid()) {
         log.warn("Invalid Permission Dto with name {}.", command.getPermissiondto().getName());
         throw IgrpResponseStatusException.of(
                 HttpStatus.CONFLICT, "Update Permission", validationResponse.getFailureMessage());
      }
      PermissionDTO newData = command.getPermissiondto();
      foundPermission.setName(newData.getName());
      if (newData.getDescription() != null && !newData.getDescription().trim().isEmpty()) {
         foundPermission.setDescription(newData.getDescription());
      }
      foundPermission.setApplication(application);
      foundPermission.setStatus(command.getPermissiondto().getStatus());
      PermissionEntity updatedPermission = permissionRepository.save(foundPermission);
      PermissionDTO response = permissionMapper.mapToDTO(updatedPermission);
      log.info("Permission with name: {} updated successfully", command.getPermissiondto().getName());
      return new ResponseEntity<>(response, HttpStatus.OK);
   }

}