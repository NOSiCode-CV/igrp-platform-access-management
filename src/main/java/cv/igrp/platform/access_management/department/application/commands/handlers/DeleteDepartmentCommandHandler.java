package cv.igrp.platform.access_management.department.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.department.application.commands.commands.DeleteDepartmentCommand;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.DepartmentRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class DeleteDepartmentCommandHandler implements CommandHandler<DeleteDepartmentCommand, ResponseEntity<Void>> {

    private final DepartmentRepository departmentRepository;

    public DeleteDepartmentCommandHandler(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @IgrpCommandHandler
    public ResponseEntity<Void> handle(DeleteDepartmentCommand command) {
        Integer id = command.getId();
        if (!departmentRepository.existsById(id)) {
            throw new EntityNotFoundException("Department not found with id: " + id);
        }

        departmentRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}