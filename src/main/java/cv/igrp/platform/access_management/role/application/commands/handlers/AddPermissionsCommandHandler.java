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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Command handler responsible for processing the {@link AddPermissionsCommand},
 * which adds a list of permissions to a given role.
 * <p>
 * This handler ensures the target role exists and is active (not deleted),
 * filters out any permissions marked as deleted, and attaches valid permissions
 * to the role. The updated role is then persisted, and the added permissions are returned as DTOs.
 */
@Slf4j
@Service
public class AddPermissionsCommandHandler implements CommandHandler<AddPermissionsCommand, ResponseEntity<List<PermissionDTO>>> {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final PermissionMapper permissionMapper;

    /**
     * Constructs the handler with necessary repositories and mappers.
     *
     * @param permissionRepository repository used to fetch permissions
     * @param roleRepository       repository used to retrieve and save roles
     * @param permissionMapper     mapper for converting {@link Permission} entities to {@link PermissionDTO}
     */
    public AddPermissionsCommandHandler(PermissionRepository permissionRepository, RoleRepository roleRepository, PermissionMapper permissionMapper) {
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
        this.permissionMapper = permissionMapper;
    }

    /**
     * Handles the addition of permissions to a specific role.
     * <ul>
     *     <li>Fetches the list of permissions by ID, ignoring any with DELETED status.</li>
     *     <li>Validates the existence of the target role and ensures it's not deleted.</li>
     *     <li>Adds the valid permissions to the role and persists the updated role.</li>
     *     <li>Returns a list of {@link PermissionDTO} objects representing the added permissions.</li>
     * </ul>
     *
     * @param command the command containing the role ID and list of permission IDs to add
     * @return a {@link ResponseEntity} containing the added permissions
     * @throws IgrpResponseStatusException if the role is not found or is marked as deleted
     */
    @IgrpCommandHandler
    @Transactional
    public ResponseEntity<List<PermissionDTO>> handle(AddPermissionsCommand command) {
        List<Integer> permissionIdList = command.getAddPermissionsRequest().stream().toList();
        log.info("Add Permissions: {} for Role id: {}.", permissionIdList, command.getId());
        List<Permission> permissionList = permissionRepository.findAllById(permissionIdList)
                .stream()
                .filter(permission -> !permission.getStatus().equals(Status.DELETED))
                .toList();

        if (permissionList.isEmpty()) {
            log.warn("No permission available from given set: {} ", command.getAddPermissionsRequest().stream().toList());
            throw new IgrpResponseStatusException(new IgrpProblem<>(HttpStatus.NOT_FOUND, "Permissions not found", permissionIdList));
        }

        Role foundRole = roleRepository.findByIdAndStatusNot(command.getId(), Status.DELETED)
                .orElseThrow(() -> {
                    log.warn("Role with id: {} not found.", command.getId());
                    return new IgrpResponseStatusException(
                            new IgrpProblem<>(HttpStatus.NOT_FOUND, "Add Permission", "Role with id: " + command.getId() + " not found.")
                    );
                });
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
        log.info("Permissions: {} for Role id: {} added successfully.", addedPermissionIds, command.getId());
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}