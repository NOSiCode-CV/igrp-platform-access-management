package cv.igrp.platform.access_management.role.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.permission.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.role.application.commands.commands.RemovePermissionsCommand;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Role;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.RoleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class RemovePermissionsCommandHandler implements CommandHandler<RemovePermissionsCommand, ResponseEntity<List<PermissionDTO>>> {

    private final RoleRepository roleRepository;
    private final PermissionMapper permissionMapper;

    public RemovePermissionsCommandHandler(RoleRepository roleRepository, PermissionMapper permissionMapper) {
        this.roleRepository = roleRepository;
        this.permissionMapper = permissionMapper;
    }

    @IgrpCommandHandler
    @Transactional
    public ResponseEntity<List<PermissionDTO>> handle(RemovePermissionsCommand command) {
        log.info("Remove Permissions with id: {} from Role with id: {}.", command.getRemovePermissionsRequest().stream().toList(), command.getId());
        List<PermissionDTO> response = new ArrayList<>();
        Role foundRole = roleRepository.findByIdAndStatusNot(command.getId(), Status.DELETED)
                .orElseThrow(() -> {
                    log.warn("Role with id: {} not found.", command.getId());
                    return new IgrpResponseStatusException(
                            new IgrpProblem<>(HttpStatus.NOT_FOUND, "Remove Permission By Role ID", "Role with id: " + command.getId() + " not found.")
                    );
                });
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
        log.info("Permissions with id {} removed from Role with id: {} successfully.", command.getRemovePermissionsRequest().stream().toList(), command.getId());
        roleRepository.save(foundRole);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}