package cv.igrp.platform.access_management.permission.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.permission.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.permission.domain.service.PermissionValidator;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.validation.ResourceValidationResponse;
import cv.igrp.platform.access_management.shared.domain.events.DeletePermissionEvent;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
 * {@link DepartmentEntity}, updating the permission's data (name, description, department, and status),
 * and returning the updated result as a {@link PermissionDTO}.
 * </p>
 * <p>
 * If the permission or the department does not exist, an {@link IgrpResponseStatusException} with HTTP 404
 * is thrown.
 * </p>
 *
 * @see UpdatePermissionCommand
 * @see PermissionEntityRepository
 * @see DepartmentEntityRepository
 * @see PermissionMapper
 * @see PermissionEntity
 * @see DepartmentEntity
 * @see PermissionDTO
 * @see Status
 * @see IgrpResponseStatusException
 */
@Slf4j
@Component
public class UpdatePermissionCommandHandler implements CommandHandler<UpdatePermissionCommand, ResponseEntity<PermissionDTO>> {

   private final PermissionEntityRepository permissionRepository;
   private final DepartmentEntityRepository departmentRepository;
   private final PermissionMapper permissionMapper;
   private final ApplicationEventPublisher eventPublisher;

   /**
    * Constructs the handler with required dependencies.
    *
    * @param permissionRepository     repository to access and persist permission entities
    * @param departmentRepository    repository to retrieve the associated department entity
    * @param permissionMapper         mapper to convert permission entities to DTOs
    */
   public UpdatePermissionCommandHandler(PermissionEntityRepository permissionRepository, DepartmentEntityRepository departmentRepository, PermissionMapper permissionMapper, ApplicationEventPublisher eventPublisher) {
      this.permissionRepository = permissionRepository;
      this.departmentRepository = departmentRepository;
      this.permissionMapper = permissionMapper;
      this.eventPublisher = eventPublisher;
   }


   /**
    * Handles the update of a {@link PermissionEntity} based on the given {@link UpdatePermissionCommand}.
    * <ul>
    *     <li>Validates that the permission exists and is not deleted.</li>
    *     <li>Validates that the referenced department exists.</li>
    *     <li>Updates name, optional description, status and department association.</li>
    *     <li>Returns the updated entity as a {@link PermissionDTO} with status {@code 200 OK}.</li>
    * </ul>
    *
    * @param command the command containing the ID and new data for the permission
    * @return a {@link ResponseEntity} with the updated {@link PermissionDTO}
    * @throws IgrpResponseStatusException if the permission or department is not found
    */
   @IgrpCommandHandler
   @Transactional
   public ResponseEntity<PermissionDTO> handle(UpdatePermissionCommand command) {
      log.info("Update permission with name: {}", command.getPermissiondto().getName());
      PermissionEntity foundPermission = permissionRepository.findByNameAndStatusNot(command.getPermissiondto().getName(), DELETED)
              .orElseThrow(() -> {
                 log.warn("Permission with name: {} not found", command.getName());
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND, "Update Permission", "Permission with name: " + command.getPermissiondto().getName() + " not found."
                 );
              });
      PermissionDTO newData = command.getPermissiondto();
      if (newData.getDescription() != null && !newData.getDescription().trim().isEmpty()) {
         foundPermission.setDescription(newData.getDescription());
      }
      foundPermission.setStatus(command.getPermissiondto().getStatus());
      PermissionEntity updatedPermission = permissionRepository.save(foundPermission);
      PermissionDTO response = permissionMapper.mapToDTO(updatedPermission);
      log.info("Permission with name: {} updated successfully", command.getPermissiondto().getName());

      eventPublisher.publishEvent(new DeletePermissionEvent(this, command.getName()));

      return new ResponseEntity<>(response, HttpStatus.OK);
   }

}