package cv.igrp.platform.access_management.role.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.permission.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.role.application.commands.commands.AddPermissionsCommand;
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

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AddPermissionsCommandHandler implements CommandHandler<AddPermissionsCommand, ResponseEntity<List<PermissionDTO>>> {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final PermissionMapper permissionMapper;

    public AddPermissionsCommandHandler(PermissionRepository permissionRepository, RoleRepository roleRepository, PermissionMapper permissionMapper) {
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
        this.permissionMapper = permissionMapper;
    }

    @IgrpCommandHandler
    @Transactional
    public ResponseEntity<List<PermissionDTO>> handle(AddPermissionsCommand command) {
        List<Permission> permissionList = permissionRepository.findAllById(command.getAddPermissionsRequest())
                .stream()
                .filter(permission -> !permission.getStatus().equals(Status.DELETED))
                .toList();

        Role foundRole = roleRepository.findByIdAndStatusNot(command.getId(), Status.DELETED)
                .orElseThrow(() -> new IgrpResponseStatusException(
                        new IgrpProblem<>(HttpStatus.NOT_FOUND, "Add Permission", "Role with id: " + command.getId() + " not found.")
                ));
        foundRole.getPermissions().addAll(permissionList);
        Role savedRole = roleRepository.save(foundRole);

        Set<Integer> addedPermissionIds = permissionList.stream()
                .map(Permission::getId)
                .collect(Collectors.toSet());

        List<PermissionDTO> response = savedRole.getPermissions()
                .stream()
                .filter(permission -> addedPermissionIds.contains(permission.getId()))
                .map(permissionMapper::mapToDTO)
                .toList();
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

}