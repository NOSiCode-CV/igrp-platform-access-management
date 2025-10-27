package cv.igrp.platform.access_management.role.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles the update of a {@link RoleEntity} entity.
 *
 * <p>
 * This handler performs the update of a role based on the data provided in the {@link UpdateRoleCommand}.
 * It ensures the role exists and optionally resolves and validates the new department and parent role (if provided).
 * </p>
 *
 * <p>
 * The role's name, description, department, parent, and status are updated accordingly.
 * If any of the referenced entities (department or parent role) are not found, an {@link IgrpResponseStatusException} is thrown.
 * </p>
 *
 * <p>
 * The updated role is saved to the database and returned as a {@link RoleDTO} wrapped in a {@link ResponseEntity}.
 * </p>
 *
 * @see UpdateRoleCommand
 * @see RoleEntity
 * @see RoleDTO
 * @see RoleEntityRepository
 * @see DepartmentEntity
 * @see DepartmentEntityRepository
 * @see RoleMapper
 * @see IgrpResponseStatusException
 * @see Status
 */
@Slf4j
@Component
public class UpdateRoleCommandHandler implements CommandHandler<UpdateRoleCommand, ResponseEntity<RoleDTO>> {

    public static final String ERROR_TITLE = "Update Role";
    private final RoleEntityRepository roleRepository;
    private final RoleMapper roleMapper;

    /**
     * Constructs an {@code UpdateRoleCommandHandler} with the required dependencies.
     *
     * @param roleRepository the repository used to fetch and persist roles
     * @param roleMapper     the mapper used to convert {@link RoleEntity} to {@link RoleDTO}
     */
    public UpdateRoleCommandHandler(RoleEntityRepository roleRepository, RoleMapper roleMapper) {
        this.roleRepository = roleRepository;
        this.roleMapper = roleMapper;
    }

    /**
     * Handles the update operation for a role.
     *
     * <ul>
     *     <li>Fetches the target role by ID and validates it is not deleted.</li>
     *     <li>If provided, validates and loads the new department and parent role.</li>
     *     <li>Updates the role's properties (name, description, department, parent, status).</li>
     *     <li>Saves the updated role to the repository.</li>
     * </ul>
     *
     * @param command the command containing the role ID and the updated role data
     * @return a {@link ResponseEntity} containing the updated {@link RoleDTO}
     * @throws IgrpResponseStatusException if the role, department, or parent role is not found
     */
    @IgrpCommandHandler
    @Transactional
    public ResponseEntity<RoleDTO> handle(UpdateRoleCommand command) {
        log.info("Update Role with code: {}.", command.getRoledto().getCode());
        RoleDTO newData = command.getRoledto();
        RoleEntity roleToUpdate = roleRepository.findByCodeAndStatusNot(command.getCode(), Status.DELETED)
                .orElseThrow(() -> {
                    log.warn("Role with code: {} not found.", command.getRoledto().getCode());
                    return IgrpResponseStatusException.of(
                            HttpStatus.NOT_FOUND, ERROR_TITLE, "Role with code: " + command.getRoledto().getCode() + " not found."
                    );
                });

        RoleEntity parentRole = roleToUpdate.getParent();
        String parentCode = newData.getParentCode();
        if (parentCode != null) {
            if (parentCode.isBlank()) {
                parentRole = null;
            } else {
                parentRole = roleRepository.findByCodeAndStatusNot(parentCode, Status.DELETED)
                        .orElseThrow(() -> {
                            log.warn("Parent Role with code: {} not found.", newData.getParentCode());
                            return IgrpResponseStatusException.of(
                                    HttpStatus.NOT_FOUND, ERROR_TITLE, "Parent Role with code: %s not found.".formatted(newData.getParentCode())
                            );
                        });
            }
        }
        roleToUpdate.setDescription(newData.getDescription());
        roleToUpdate.setParent(parentRole);
        roleToUpdate.setStatus(newData.getStatus());
        RoleEntity updatedRole = roleRepository.save(roleToUpdate);
        log.info("Role with code: {} updated successfully.", command.getRoledto().getCode());
        return new ResponseEntity<>(roleMapper.mapToDto(updatedRole), HttpStatus.OK);
    }

}