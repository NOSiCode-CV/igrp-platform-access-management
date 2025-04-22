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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
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
    @Transactional
    public ResponseEntity<PermissionDTO> handle(CreatePermissionCommand command) {
        log.info("Create permission with name: {}", command.getPermissiondto().getName());
        PermissionDTO request = command.getPermissiondto();
        Application foundApplication = applicationRepository.findById(command.getPermissiondto().getApplicationId())
                .orElseThrow(() -> {
                    log.warn("Application with id {} not found.", command.getPermissiondto().getApplicationId());
                    return new IgrpResponseStatusException(
                            new IgrpProblem<>(HttpStatus.NOT_FOUND, "Create Permission", "Application with id: " + command.getPermissiondto().getApplicationId() + " not found.")
                    );
                });
        Permission newPermission = new Permission();
        newPermission.setStatus(request.getStatus());
        newPermission.setName(request.getName());
        if (request.getDescription() != null) {
            newPermission.setDescription(request.getDescription().trim());
        }
        newPermission.setApplication(foundApplication);
        Permission savedPermission = repository.save(newPermission);
        PermissionDTO permissionDTO = permissionMapper.mapToDTO(savedPermission);
        log.info("Permission with name: {} created successfully.", command.getPermissiondto().getName());
        return new ResponseEntity<>(permissionDTO, HttpStatus.CREATED);
    }

}