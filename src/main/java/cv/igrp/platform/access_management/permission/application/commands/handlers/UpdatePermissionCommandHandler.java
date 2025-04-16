package cv.igrp.platform.access_management.permission.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.permission.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.domain.models.Permission;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ApplicationRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.PermissionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.permission.application.commands.commands.UpdatePermissionCommand;


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
    public ResponseEntity<PermissionDTO> handle(UpdatePermissionCommand command) {
        Permission foundPermission = permissionRepository.findById(command.getId())
                .orElseThrow(() -> new IgrpResponseStatusException(
                        new IgrpProblem<>(HttpStatus.NOT_FOUND, "Update Permission", "Permission with id: " + command.getId() + " not found.")
                ));
        Application application = applicationRepository.findById(command.getPermissiondto().getApplicationId())
                .orElseThrow(() -> new IgrpResponseStatusException(
                        new IgrpProblem<>(HttpStatus.NOT_FOUND, "Update Permission", "Application with id: " + command.getId() + " not found.")
                ));

        PermissionDTO newData = command.getPermissiondto();
        foundPermission.setName(newData.getName());
        if (newData.getDescription() != null && !newData.getDescription().trim().isEmpty()) {
            foundPermission.setDescription(newData.getDescription());
        }
        foundPermission.setApplication(application);
        foundPermission.setStatus(command.getPermissiondto().getStatus());
        Permission updatedPermission = permissionRepository.save(foundPermission);
        PermissionDTO response = permissionMapper.mapToDTO(updatedPermission);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

}