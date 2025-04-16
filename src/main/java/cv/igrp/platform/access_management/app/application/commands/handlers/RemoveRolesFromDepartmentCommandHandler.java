package cv.igrp.platform.access_management.app.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.DepartmentRepository;
import cv.igrp.platform.access_management.shared.domain.models.Department;
import cv.igrp.platform.access_management.app.application.dto.RoleDTO;
import cv.igrp.platform.access_management.app.application.commands.commands.RemoveRolesFromDepartmentCommand;
import cv.igrp.platform.access_management.app.mapper.RoleMapper;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RemoveRolesFromDepartmentCommandHandler implements CommandHandler<RemoveRolesFromDepartmentCommand, ResponseEntity<List<RoleDTO>>> {

    private final DepartmentRepository departmentRepository;
    private final RoleMapper roleMapper;

    public RemoveRolesFromDepartmentCommandHandler(DepartmentRepository departmentRepository, RoleMapper roleMapper) {
        this.departmentRepository = departmentRepository;
        this.roleMapper = roleMapper;
    }

    @IgrpCommandHandler
    public ResponseEntity<List<RoleDTO>> handle(RemoveRolesFromDepartmentCommand command) {
        Department department = departmentRepository.findById(command.getDepartmentId())
                .orElseThrow(() -> new EntityNotFoundException("Department not found with id: " + command.getDepartmentId()));

        department.getRoles().removeIf(role -> command.getRoleIds().contains(role.getId()));
        departmentRepository.save(department);

        List<RoleDTO> result = department.getRoles().stream().map(roleMapper::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }
}