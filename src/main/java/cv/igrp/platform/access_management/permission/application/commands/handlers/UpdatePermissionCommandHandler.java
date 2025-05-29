package cv.igrp.platform.access_management.permission.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.permission.application.commands.commands.UpdatePermissionCommand;
import cv.igrp.platform.access_management.permission.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.permission.domain.service.PermissionValidator;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.domain.models.Permission;
import cv.igrp.platform.access_management.shared.domain.validation.ResourceValidationResponse;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ApplicationRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.PermissionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static cv.igrp.platform.access_management.shared.application.constants.Status.DELETED;


/**
 * Handles the update operation for a {@link Permission}.
 * <p>
 * This command handler is responsible for validating the existence of the permission and associated
 * {@link Application}, updating the permission's data (name, description, application, and status),
 * and returning the updated result as a {@link PermissionDTO}.
 * </p>
 * <p>
 * If the permission or the application does not exist, an {@link IgrpResponseStatusException} with HTTP 404
 * is thrown.
 * </p>
 *
 * @see UpdatePermissionCommand
 * @see PermissionRepository
 * @see ApplicationRepository
 * @see PermissionMapper
 * @see Permission
 * @see Application
 * @see PermissionDTO
 * @see Status
 * @see IgrpResponseStatusException
 */
@Slf4j
@Service
public class UpdatePermissionCommandHandler implements CommandHandler<UpdatePermissionCommand, ResponseEntity<PermissionDTO>> {

    private final PermissionRepository permissionRepository;
    private final ApplicationRepository applicationRepository;
    private final PermissionMapper permissionMapper;

    /**
     * Constructs the handler with required dependencies.
     *
     * @param permissionRepository     repository to access and persist permission entities
     * @param applicationRepository    repository to retrieve the associated application entity
     * @param permissionMapper         mapper to convert permission entities to DTOs
     */
    public UpdatePermissionCommandHandler(PermissionRepository permissionRepository, ApplicationRepository applicationRepository, PermissionMapper permissionMapper) {
        this.permissionRepository = permissionRepository;
        this.applicationRepository = applicationRepository;
        this.permissionMapper = permissionMapper;
    }


    /**
     * Handles the update of a {@link Permission} based on the given {@link UpdatePermissionCommand}.
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
        log.info("Update permission with id: {}", command.getId());
        Permission foundPermission = permissionRepository.findByIdAndStatusNot(command.getId(), DELETED)
                .orElseThrow(() -> {
                    log.warn("Permission with id: {} not found", command.getId());
                    return new IgrpResponseStatusException(
                            new IgrpProblem<>(HttpStatus.NOT_FOUND, "Update Permission", "Permission with id: " + command.getId() + " not found.")
                    );
                });
        Application application = applicationRepository.findById(command.getPermissiondto().getApplicationId())
                .orElseThrow(() -> {
                    log.warn("Application with id: {} not found", command.getPermissiondto().getApplicationId());
                    return new IgrpResponseStatusException(
                            new IgrpProblem<>(HttpStatus.NOT_FOUND, "Update Permission", "Application with id: " + command.getId() + " not found.")
                    );
                });
        ResourceValidationResponse validationResponse = PermissionValidator.validatePermissionName(command.getPermissiondto(), application);
        if (!validationResponse.isValid()) {
            log.warn("Invalid Permission Dto with id {}.", command.getId());
            throw new IgrpResponseStatusException(
                    new IgrpProblem<>(HttpStatus.CONFLICT, "Update Permission", validationResponse.getFailureMessage()));
        }
        PermissionDTO newData = command.getPermissiondto();
        foundPermission.setName(newData.getName());
        if (newData.getDescription() != null && !newData.getDescription().trim().isEmpty()) {
            foundPermission.setDescription(newData.getDescription());
        }
        foundPermission.setApplication(application);
        foundPermission.setStatus(command.getPermissiondto().getStatus());
        Permission updatedPermission = permissionRepository.save(foundPermission);
        PermissionDTO response = permissionMapper.mapToDTO(updatedPermission);
        log.info("Permission with id: {} updated successfully", command.getId());
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}