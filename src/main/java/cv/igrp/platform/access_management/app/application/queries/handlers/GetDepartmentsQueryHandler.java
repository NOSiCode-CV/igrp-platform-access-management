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

// GET ALL
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
        List<DepartmentDTO> dtos = departments.stream().map(departmentMapper::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }
}