package cv.igrp.platform.access_management.role.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.role.application.commands.commands.UpdateRoleCommand;
import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.role.domain.service.RoleValidator;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Department;
import cv.igrp.platform.access_management.shared.domain.models.Role;
import cv.igrp.platform.access_management.shared.domain.validation.ResourceValidationResponse;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.DepartmentRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.RoleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles the update of a {@link Role} entity.
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
 * @see Role
 * @see RoleDTO
 * @see RoleRepository
 * @see Department
 * @see DepartmentRepository
 * @see RoleMapper
 * @see IgrpResponseStatusException
 * @see IgrpProblem
 * @see Status
 */
@Slf4j
@Service
public class UpdateRoleCommandHandler implements CommandHandler<UpdateRoleCommand, ResponseEntity<RoleDTO>> {

    public static final String ERROR_TITLE = "Update Role";
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final RoleMapper roleMapper;

    /**
     * Constructs an {@code UpdateRoleCommandHandler} with the required dependencies.
     *
     * @param roleRepository        the repository used to fetch and persist roles
     * @param departmentRepository  the repository used to retrieve departments by ID
     * @param roleMapper            the mapper used to convert {@link Role} to {@link RoleDTO}
     */
    public UpdateRoleCommandHandler(RoleRepository roleRepository, DepartmentRepository departmentRepository, RoleMapper roleMapper) {

        this.roleRepository = roleRepository;
        this.departmentRepository = departmentRepository;
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
        log.info("Update Role with id: {}.", command.getId());
        RoleDTO newData = command.getRoledto();
        Department department = null;
        Role parentRole = null;
        Role roleToUpdate = roleRepository.findByIdAndStatusNot(command.getId(), Status.DELETED)
                .orElseThrow(() -> {
                    log.warn("Role with id: {} not found.", command.getId());
                    return IgrpResponseStatusException.of(
                            HttpStatus.NOT_FOUND, ERROR_TITLE, "Role with id: " + command.getId() + " not found."
                    );
                });
        if (newData.getDepartmentId() != null) {
            department = departmentRepository.findById(newData.getDepartmentId())
                    .orElseThrow(() -> {
                        log.warn("Department with id: {} not found.", command.getRoledto().getDepartmentId());
                        return IgrpResponseStatusException.of(
                                HttpStatus.NOT_FOUND, ERROR_TITLE, "Department with id: " + newData.getDepartmentId() + " not found."
                        );
                    });
            ResourceValidationResponse roleValidationResponse = RoleValidator.validateRoleDto(command.getRoledto(), department);
            if(!roleValidationResponse.isValid()){
                throw IgrpResponseStatusException.of(
                        HttpStatus.CONFLICT, ERROR_TITLE, roleValidationResponse.getFailureMessage()
                );
            }
        }
        if (newData.getParentId() != null) {
            parentRole = roleRepository.findById(newData.getParentId())
                    .orElseThrow(() -> {
                        log.warn("Parent Role with id: {} not found.", newData.getParentId());
                        return IgrpResponseStatusException.of(
                                HttpStatus.NOT_FOUND, ERROR_TITLE, "Parent Role with id: " + newData.getParentId() + " not found."
                        );
                    });
        }
        roleToUpdate.setName(newData.getName());
        roleToUpdate.setDescription(newData.getDescription());
        roleToUpdate.setDepartment(department);
        roleToUpdate.setParent(parentRole);
        roleToUpdate.setStatus(newData.getStatus());
        Role updatedRole = roleRepository.save(roleToUpdate);
        log.info("Role with id: {} updated successfully.", command.getId());
        return new ResponseEntity<>(roleMapper.mapToDto(updatedRole), HttpStatus.OK);
    }
}