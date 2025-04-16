package cv.igrp.platform.access_management.department.application.commands.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.department.application.queries.queries.GetDepartmentByIdQuery;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.DepartmentRepository;
import cv.igrp.platform.access_management.shared.domain.models.Department;
import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;
import cv.igrp.platform.access_management.department.mapper.DepartmentMapper;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

// GET BY ID
@Service
public class GetDepartmentByIdQueryHandler implements QueryHandler<GetDepartmentByIdQuery, ResponseEntity<DepartmentDTO>> {
    private final DepartmentRepository departmentRepository;
    private final DepartmentMapper departmentMapper;

    public GetDepartmentByIdQueryHandler(DepartmentRepository departmentRepository, DepartmentMapper departmentMapper) {
        this.departmentRepository = departmentRepository;
        this.departmentMapper = departmentMapper;
    }

    @IgrpQueryHandler
    public ResponseEntity<DepartmentDTO> handle(GetDepartmentByIdQuery query) {
        Department department = departmentRepository.findById(query.getId())
                .orElseThrow(() -> new EntityNotFoundException("Department not found with id: " + query.getId()));
        return ResponseEntity.ok(departmentMapper.toDto(department));
    }
}