package cv.igrp.platform.access_management.role.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.role.application.commands.commands.CreateRoleCommand;
import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.role.domain.service.RoleValidator;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
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
 * Command handler responsible for processing the {@link CreateRoleCommand}
 * to create a new role in the system.
 * <p>
 * This handler ensures that the specified department exists and, if provided, that the parent role exists
 * and is not marked as deleted. It maps the input data to the corresponding entity, persists the new role,
 * and returns its DTO representation.
 * @see CreateRoleCommand
 * @see RoleDTO
 * @see Role
 * @see RoleRepository
 * @see RoleMapper
 * @see Department
 * @see DepartmentRepository
 * @see Status
 * @see IgrpResponseStatusException
 */
@Slf4j
@Service
public class CreateRoleCommandHandler implements CommandHandler<CreateRoleCommand, ResponseEntity<RoleDTO>> {

    private final DepartmentRepository departmentRepository;
    private final RoleRepository roleRepository;
    private final RoleMapper roleMapper;

    /**
     * Constructs the role creation handler with required dependencies.
     *
     * @param departmentRepository repository to access department data
     * @param roleRepository repository to manage roles
     * @param roleMapper mapper to convert between entity and DTO
     */
    public CreateRoleCommandHandler(DepartmentRepository departmentRepository, RoleRepository roleRepository, RoleMapper roleMapper) {

        this.departmentRepository = departmentRepository;
        this.roleRepository = roleRepository;
        this.roleMapper = roleMapper;
    }

    /**
     * Handles the creation of a new role using the data provided in the {@link CreateRoleCommand}.
     * <ul>
     *     <li>Validates the existence of the associated department.</li>
     *     <li>If a parent role ID is provided, validates its existence and status.</li>
     *     <li>Maps the DTO to a Role entity, persists it, and returns the result as a DTO.</li>
     * </ul>
     *
     * @param command the command containing the role data to be created
     * @return a {@link ResponseEntity} containing the created {@link RoleDTO}
     * @throws IgrpResponseStatusException if the department or parent role is not found
     */
    @IgrpCommandHandler
    @Transactional
    public ResponseEntity<RoleDTO> handle(CreateRoleCommand command) {
        log.info("Create Role with name: {}.", command.getRoledto().getName());
        RoleDTO request = command.getRoledto();
        Role parentRole = null;
        Department department = departmentRepository.findById(command.getRoledto().getDepartmentId())
                .orElseThrow(() -> {
                    log.warn("Department with id: {} not found.", command.getRoledto().getDepartmentId());
                    return new IgrpResponseStatusException(
                            new IgrpProblem<>(HttpStatus.NOT_FOUND, "Create Role", "Department with id: " + command.getRoledto().getDepartmentId() + " not found.")
                    );
                });
        ResourceValidationResponse roleValidationResponse = RoleValidator.validateRoleDto(command.getRoledto(), department);
        if(!roleValidationResponse.isValid()){
            throw new IgrpResponseStatusException(
                    new IgrpProblem<>(HttpStatus.CONFLICT, "Create Role", roleValidationResponse.getFailureMessage())
            );
        }

        if (command.getRoledto().getParentId() != null) {
            Integer parentRoleId = command.getRoledto().getParentId();
            parentRole = roleRepository.findByIdAndStatusNot(parentRoleId, Status.DELETED)
                    .orElseThrow(() -> {
                        log.warn("Parent Role with id: {} not found.", command.getRoledto().getParentId());
                        return new IgrpResponseStatusException(
                                new IgrpProblem<>(HttpStatus.NOT_FOUND, "Create Role", "Parent Role with id: " + parentRoleId + " not found.")
                        );
                    });
        }

        Role newRole = roleMapper.mapToEntity(request, department, parentRole);
        Role savedRole = roleRepository.save(newRole);
        RoleDTO roleDTO = roleMapper.mapToDto(savedRole);
        log.info("Role with name: {} created successfully.", command.getRoledto().getName());
        return new ResponseEntity<>(roleDTO, HttpStatus.CREATED);
    }
}