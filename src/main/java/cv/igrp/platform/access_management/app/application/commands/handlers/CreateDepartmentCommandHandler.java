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

@Service
public class CreateDepartmentCommandHandler implements CommandHandler<CreateDepartmentCommand, ResponseEntity<DepartmentDTO>> {

    private final DepartmentRepository departmentRepository;
    private final DepartmentMapper departmentMapper;

    public CreateDepartmentCommandHandler(DepartmentRepository departmentRepository, DepartmentMapper departmentMapper) {
        this.departmentRepository = departmentRepository;
        this.departmentMapper = departmentMapper;
    }

    @IgrpCommandHandler
    public ResponseEntity<DepartmentDTO> handle(CreateDepartmentCommand command) {
        Department department = departmentMapper.toEntity(command.getDepartmentDTO());
        department.setId(null);
        Department saved = departmentRepository.save(department);
        return ResponseEntity.status(HttpStatus.CREATED).body(departmentMapper.toDto(saved));
    }
}