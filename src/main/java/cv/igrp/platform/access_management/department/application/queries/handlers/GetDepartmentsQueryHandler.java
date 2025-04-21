package cv.igrp.platform.access_management.department.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;

import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.department.application.queries.queries.GetDepartmentsQuery;
import cv.igrp.platform.access_management.department.mapper.DepartmentMapper;
import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;
import cv.igrp.platform.access_management.shared.domain.models.Department;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.DepartmentRepository;

@Service
public class GetDepartmentsQueryHandler implements QueryHandler<GetDepartmentsQuery, ResponseEntity<List<DepartmentDTO>>> {

    private final DepartmentRepository departmentRepository;
    private final DepartmentMapper departmentMapper;

    public GetDepartmentsQueryHandler(DepartmentRepository departmentRepository, DepartmentMapper departmentMapper) {
        this.departmentRepository = departmentRepository;
        this.departmentMapper = departmentMapper;
    }

    @IgrpQueryHandler
    public ResponseEntity<List<DepartmentDTO>> handle(GetDepartmentsQuery query) {
        List<Department> departments = departmentRepository.findAll();
        List<DepartmentDTO> dtos = departments.stream()
            .map(departmentMapper::toDto)
            .toList();

        return ResponseEntity.ok(dtos);
    }
}