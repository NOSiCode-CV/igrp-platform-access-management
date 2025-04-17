package cv.igrp.platform.access_management.department.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.department.application.commands.commands.PostDepartmentCommand;
import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;
import cv.igrp.platform.access_management.shared.domain.models.Department;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.DepartmentRepository;
import cv.igrp.platform.access_management.department.mapper.DepartmentMapper;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class PostDepartmentCommandHandler implements CommandHandler<PostDepartmentCommand, ResponseEntity<DepartmentDTO>> {

    private final DepartmentRepository departmentRepository;
    private final DepartmentMapper departmentMapper;

    public PostDepartmentCommandHandler(DepartmentRepository departmentRepository, DepartmentMapper departmentMapper) {
        this.departmentRepository = departmentRepository;
        this.departmentMapper = departmentMapper;
    }

    @IgrpCommandHandler
    public ResponseEntity<DepartmentDTO> handle(PostDepartmentCommand command) {
        Department department = departmentMapper.toEntity(command.getDepartmentDTO());
        department.setId(null); // garante criação nova
        Department saved = departmentRepository.save(department);
        DepartmentDTO result = departmentMapper.toDto(saved);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}