package cv.igrp.platform.access_management.role.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.role.application.commands.commands.CreateRoleCommand;
import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Department;
import cv.igrp.platform.access_management.shared.domain.models.Role;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.DepartmentRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.RoleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class CreateRoleCommandHandler implements CommandHandler<CreateRoleCommand, ResponseEntity<RoleDTO>> {

    private final DepartmentRepository departmentRepository;
    private final RoleRepository roleRepository;
    private final RoleMapper roleMapper;

    public CreateRoleCommandHandler(DepartmentRepository departmentRepository, RoleRepository roleRepository, RoleMapper roleMapper) {

        this.departmentRepository = departmentRepository;
        this.roleRepository = roleRepository;
        this.roleMapper = roleMapper;
    }

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