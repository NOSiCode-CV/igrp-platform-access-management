package cv.igrp.platform.access_management.department.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.department.application.commands.commands.UpdateDepartmentCommand;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.DepartmentRepository;
import cv.igrp.platform.access_management.shared.domain.models.Department;
import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;
import cv.igrp.platform.access_management.department.mapper.DepartmentMapper;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

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

        // Atualizar os dados da entidade com o DTO
        departmentMapper.updateEntityFromDto(command.getDepartmentdto(), department);
        
        Department updated = departmentRepository.save(department);
        return ResponseEntity.ok(departmentMapper.toDto(updated));
    }
}

