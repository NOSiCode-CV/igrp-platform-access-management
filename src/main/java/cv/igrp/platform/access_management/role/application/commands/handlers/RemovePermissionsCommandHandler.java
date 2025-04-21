package cv.igrp.platform.access_management.role.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.permission.domain.service.PermissionMapper;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class RemovePermissionsCommandHandler implements CommandHandler<RemovePermissionsCommand, ResponseEntity<List<PermissionDTO>>> {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final PermissionMapper permissionMapper;

    public RemovePermissionsCommandHandler(RoleRepository roleRepository, PermissionRepository permissionRepository, PermissionMapper permissionMapper) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.permissionMapper = permissionMapper;
    }

    @IgrpCommandHandler
    @Transactional
    public ResponseEntity<List<PermissionDTO>> handle(RemovePermissionsCommand command) {

        List<PermissionDTO> response = new ArrayList<>();
        Role foundRole = roleRepository.findByIdAndStatusNot(command.getId(), Status.DELETED)
                .orElseThrow(() -> new IgrpResponseStatusException(
                        new IgrpProblem<>(HttpStatus.NOT_FOUND, "Remove Permission By Role ID", "Role with id: " + command.getId() + " not found.")
                ));
        for (Integer permissionId : command.getRemovePermissionsRequest()) {
            foundRole.getPermissions()
                    .stream()
                    .filter(permission -> permission.getId().equals(permissionId))
                    .findFirst()
                    .ifPresent(permission -> {
                        foundRole.getPermissions().remove(permission);
                        response.add(permissionMapper.mapToDTO(permission));
                    });
        }
        roleRepository.save(foundRole);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}