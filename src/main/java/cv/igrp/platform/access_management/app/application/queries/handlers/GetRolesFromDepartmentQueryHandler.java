package cv.igrp.platform.access_management.app.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.DepartmentRepository;
import cv.igrp.platform.access_management.shared.domain.models.Department;
import cv.igrp.platform.access_management.app.application.dto.DepartmentDTO;
import cv.igrp.platform.access_management.app.mapper.DepartmentMapper;
import cv.igrp.platform.access_management.app.application.commands.commands.CreateDepartmentCommand;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

// GET ROLES FROM DEPARTMENT
@Service
public class GetRolesFromDepartmentQueryHandler implements QueryHandler<GetRolesFromDepartmentQuery, ResponseEntity<List<RoleDTO>>> {
    private final DepartmentRepository departmentRepository;
    private final RoleMapper roleMapper;

    public GetRolesFromDepartmentQueryHandler(DepartmentRepository departmentRepository, RoleMapper roleMapper) {
        this.departmentRepository = departmentRepository;
        this.roleMapper = roleMapper;
    }

    @IgrpQueryHandler
    public ResponseEntity<List<RoleDTO>> handle(GetRolesFromDepartmentQuery query) {
        Department department = departmentRepository.findById(query.getDepartmentId())
                .orElseThrow(() -> new EntityNotFoundException("Department not found with id: " + query.getDepartmentId()));
        List<RoleDTO> roles = department.getRoles().stream().map(roleMapper::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(roles);
    }
}