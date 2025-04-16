package cv.igrp.platform.access_management.permission.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.permission.application.commands.commands.CreatePermissionCommand;
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

@Service
public class CreatePermissionCommandHandler implements CommandHandler<CreatePermissionCommand, ResponseEntity<PermissionDTO>> {

    private final PermissionRepository repository;
    private final ApplicationRepository applicationRepository;
    private final PermissionMapper permissionMapper;

    public CreatePermissionCommandHandler(PermissionRepository repository, ApplicationRepository applicationRepository, PermissionMapper permissionMapper) {

        this.repository = repository;
        this.applicationRepository = applicationRepository;
        this.permissionMapper = permissionMapper;
    }

    @IgrpCommandHandler
    public ResponseEntity<PermissionDTO> handle(CreatePermissionCommand command) {
        PermissionDTO request = command.getPermissiondto();
        Application foundApplication = applicationRepository.findById(command.getPermissiondto().getApplicationId())
                .orElseThrow(() -> new IgrpResponseStatusException(
                        new IgrpProblem<>(HttpStatus.NOT_FOUND, "Create Permission", "Application with id: " + command.getPermissiondto().getApplicationId() + " not found.")
                ));
        Permission newPermission = new Permission();
        newPermission.setStatus(request.getStatus());
        newPermission.setName(request.getName());
        if (request.getDescription() != null) {
            newPermission.setDescription(request.getDescription().trim());
        }
        newPermission.setApplication(foundApplication);
        Permission savedPermission = repository.save(newPermission);
        PermissionDTO permissionDTO = permissionMapper.mapToDTO(savedPermission);
        return new ResponseEntity<>(permissionDTO, HttpStatus.CREATED);
    }

}