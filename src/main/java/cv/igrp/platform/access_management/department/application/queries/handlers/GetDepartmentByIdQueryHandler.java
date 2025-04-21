package cv.igrp.platform.access_management.department.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.department.application.queries.queries.GetDepartmentByIdQuery;
import cv.igrp.platform.access_management.department.mapper.DepartmentMapper;
import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;
import cv.igrp.platform.access_management.shared.domain.models.Department;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.DepartmentRepository;
import jakarta.persistence.EntityNotFoundException;


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

        DepartmentDTO dto = departmentMapper.toDto(department);
        return ResponseEntity.ok(dto);
    }
}
