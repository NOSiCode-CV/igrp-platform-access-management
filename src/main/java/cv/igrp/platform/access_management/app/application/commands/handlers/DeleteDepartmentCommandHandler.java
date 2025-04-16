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

// DELETE
@Service
public class DeleteDepartmentCommandHandler implements CommandHandler<DeleteDepartmentCommand, ResponseEntity<Void>> {
    private final DepartmentRepository departmentRepository;

    public DeleteDepartmentCommandHandler(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @IgrpCommandHandler
    public ResponseEntity<Void> handle(DeleteDepartmentCommand command) {
        Department department = departmentRepository.findById(command.getId())
                .orElseThrow(() -> new EntityNotFoundException("Department not found with id: " + command.getId()));

        departmentRepository.delete(department);
        return ResponseEntity.noContent().build();
    }
}
