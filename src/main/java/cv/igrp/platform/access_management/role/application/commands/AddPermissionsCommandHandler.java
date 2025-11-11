package cv.igrp.platform.access_management.role.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.permission.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
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
 *
 * @see AddPermissionsCommand
 * @see PermissionEntity
 * @see PermissionDTO
 * @see PermissionEntityRepository
 * @see RoleEntity
 * @see RoleEntityRepository
 * @see PermissionMapper
 * @see IgrpResponseStatusException
 * @see Status
 */
@Slf4j
@Component
public class AddPermissionsCommandHandler implements CommandHandler<AddPermissionsCommand, ResponseEntity<RoleDTO>> {

    private final PermissionEntityRepository permissionRepository;
    private final RoleEntityRepository roleRepository;
    private final RoleMapper roleMapper;

    /**
     * Constructs the handler with necessary repositories and mappers.
     *
     * @param permissionRepository repository used to fetch permissions
     * @param roleRepository       repository used to retrieve and save roles
     * @param roleMapper           mapper for converting {@link RoleEntity} entities to {@link RoleDTO}
     */
    public AddPermissionsCommandHandler(PermissionEntityRepository permissionRepository, RoleEntityRepository roleRepository, RoleMapper roleMapper) {
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
        this.roleMapper = roleMapper;
    }

    /**
     * Handles the addition of permissions to a specific role.
     * <ul>
     *     <li>Fetches the list of permissions by ID, ignoring any with DELETED status.</li>
     *     <li>Validates the existence of the target role and ensures it's not deleted.</li>
     *     <li>Adds the valid permissions to the role and persists the updated role.</li>
     *     <li>Returns {@link RoleDTO} object representing the role.</li>
     * </ul>
     *
     * @param command the command containing the role ID and a list of permission IDs to add
     * @return a {@link ResponseEntity} containing the role with added permissions
     * @throws IgrpResponseStatusException if the role is not found or is marked as deleted
     */
    @IgrpCommandHandler
    @Transactional
    public ResponseEntity<RoleDTO> handle(AddPermissionsCommand command) {
        List<String> permissionIdList = command.getAddPermissionsRequest().stream().toList();
        log.info("Add Permissions: {} for Role code: {}.", permissionIdList, command.getCode());
        List<PermissionEntity> permissionList = permissionRepository.findAllByNameIn(permissionIdList)
                .stream()
                .filter(permission -> !permission.getStatus().equals(Status.DELETED))
                .toList();

        if (permissionList.isEmpty()) {
            log.warn("No permission available from given set: {} ", command.getAddPermissionsRequest().stream().toList());
            throw IgrpResponseStatusException.of(HttpStatus.NOT_FOUND, "Permissions not found", permissionIdList);
        }

        RoleEntity foundRole = roleRepository.findByCodeAndStatusNot(command.getCode(), Status.DELETED)
                .orElseThrow(() -> {
                    log.warn("Role with code: {} not found.", command.getCode());
                    return IgrpResponseStatusException.of(
                            HttpStatus.NOT_FOUND, "Add Permission", "Role with code: " + command.getCode() + " not found."
                    );
                });

        if(foundRole.getParent() != null)
            permissionList = permissionList.stream()
                    .filter(p -> foundRole.getParent().getPermissions().contains(p))
                    .toList();

        foundRole.getPermissions().addAll(permissionList);
        RoleEntity savedRole = roleRepository.save(foundRole);

        Set<Integer> addedPermissionIds = permissionList.stream()
                .map(PermissionEntity::getId)
                .collect(Collectors.toSet());

        RoleDTO response = roleMapper.mapToDto(savedRole);

        log.info("Permissions: {} for Role code: {} added successfully.", addedPermissionIds, command.getCode());
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

}