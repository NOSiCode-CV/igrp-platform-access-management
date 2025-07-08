package cv.igrp.platform.access_management.department.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.department.application.queries.queries.GetRolesFromDepartmentQuery;
import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.DepartmentRepository;
import cv.igrp.platform.access_management.shared.domain.models.Department;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;

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
        List<RoleDTO> roles = department.getRoles().stream().map(roleMapper::mapToDto).toList();
        return ResponseEntity.ok(roles);
    }
}