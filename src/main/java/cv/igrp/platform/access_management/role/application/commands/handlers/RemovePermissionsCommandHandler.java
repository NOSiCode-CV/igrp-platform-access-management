package cv.igrp.platform.access_management.role.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.role.application.commands.commands.RemovePermissionsCommand;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Permission;
import cv.igrp.platform.access_management.shared.domain.models.Role;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.PermissionRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.RoleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RemovePermissionsCommandHandler implements CommandHandler<RemovePermissionsCommand, ResponseEntity<List<PermissionDTO>>> {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public RemovePermissionsCommandHandler(RoleRepository roleRepository, PermissionRepository permissionRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    @IgrpCommandHandler
    public ResponseEntity<List<PermissionDTO>> handle(RemovePermissionsCommand command) {

        List<PermissionDTO> response = new ArrayList<>();
        Role foundRole = roleRepository.findById(command.getId())
                .orElseThrow(() -> new IgrpResponseStatusException(
                        new IgrpProblem<>(HttpStatus.NOT_FOUND, "Remove Permission By Role ID", "Role with id: " + command.getId() + " not found.")
                ));
        for (Integer permissionId : command.getRemovePermissionsRequest()) {
            foundRole.getPermissions()
                    .stream()
                    .filter(permission -> permission.getId().equals(permissionId))
                    .findFirst()
                    .ifPresent(permission -> {
                        permission.setStatus(Status.DELETED);
                        permissionRepository.save(permission);
                        response.add(toDTO(permission));
                    });
        }
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    private PermissionDTO toDTO(Permission permission) {
        PermissionDTO dto = new PermissionDTO();
        dto.setId(permission.getId());
        dto.setName(permission.getName());
        dto.setDescription(permission.getDescription());
        dto.setStatus(permission.getStatus());
        return dto;
    }
}