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

// UPDATE
@Service
public class UpdateDepartmentCommandHandler implements CommandHandler<UpdateDepartmentCommand, ResponseEntity<DepartmentDTO>> {
    private final DepartmentRepository departmentRepository;
    private final DepartmentMapper departmentMapper;

    public UpdateDepartmentCommandHandler(DepartmentRepository departmentRepository, DepartmentMapper departmentMapper) {
        this.departmentRepository = departmentRepository;
        this.departmentMapper = departmentMapper;
    }

    @IgrpCommandHandler
    public ResponseEntity<DepartmentDTO> handle(UpdateDepartmentCommand command) {
        Department department = departmentRepository.findById(command.getId())
                .orElseThrow(() -> new EntityNotFoundException("Department not found with id: " + command.getId()));

        departmentMapper.updateEntityFromDto(command.getDepartmentDTO(), department);
        Department updated = departmentRepository.save(department);
        return ResponseEntity.ok(departmentMapper.toDto(updated));
    }
}
