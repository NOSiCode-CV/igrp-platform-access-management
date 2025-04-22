package cv.igrp.platform.access_management.permission.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.permission.application.commands.commands.UpdatePermissionCommand;
import cv.igrp.platform.access_management.permission.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.domain.models.Permission;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ApplicationRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.PermissionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static cv.igrp.platform.access_management.shared.application.constants.Status.DELETED;


@Slf4j
@Service
public class UpdatePermissionCommandHandler implements CommandHandler<UpdatePermissionCommand, ResponseEntity<PermissionDTO>> {

    private final PermissionRepository permissionRepository;
    private final ApplicationRepository applicationRepository;
    private final PermissionMapper permissionMapper;

    public UpdatePermissionCommandHandler(PermissionRepository permissionRepository, ApplicationRepository applicationRepository, PermissionMapper permissionMapper) {
        this.permissionRepository = permissionRepository;
        this.applicationRepository = applicationRepository;
        this.permissionMapper = permissionMapper;
    }

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