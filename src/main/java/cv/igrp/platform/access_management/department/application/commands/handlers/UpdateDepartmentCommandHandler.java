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

/**
 * Command handler responsible for processing {@link UpdateDepartmentCommand} to update an existing department.
 * <p>
 * It performs the following steps:
 * <ul>
 *     <li>Fetches the department by ID from the {@link DepartmentRepository}.</li>
 *     <li>If not found, throws {@link EntityNotFoundException}.</li>
 *     <li>Updates the existing {@link Department} entity with values from the {@link DepartmentDTO} using {@link DepartmentMapper}.</li>
 *     <li>Saves the updated entity back to the repository.</li>
 *     <li>Returns the updated department as a {@link DepartmentDTO} in an HTTP 200 OK response.</li>
 * </ul>
 *
 */
@Service
public class UpdateDepartmentCommandHandler implements CommandHandler<UpdateDepartmentCommand, ResponseEntity<DepartmentDTO>> {
    private final DepartmentRepository departmentRepository;
    private final DepartmentMapper departmentMapper;

    /**
     * Constructs the command handler with required dependencies.
     *
     * @param departmentRepository the repository used to retrieve and save department entities
     * @param departmentMapper the mapper used to convert between DTOs and entities
     */
    public UpdateDepartmentCommandHandler(DepartmentRepository departmentRepository, DepartmentMapper departmentMapper) {
        this.departmentRepository = departmentRepository;
        this.departmentMapper = departmentMapper;
    }

    /**
     * Handles the update of an existing department.
     *
     * @param command the update command containing the department ID and updated data
     * @return a {@link ResponseEntity} with status 200 OK and the updated department DTO
     * @throws EntityNotFoundException if no department is found with the given ID
     */
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

