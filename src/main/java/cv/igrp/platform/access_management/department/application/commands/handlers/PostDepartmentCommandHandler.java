package cv.igrp.platform.access_management.department.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.department.application.commands.commands.PostDepartmentCommand;
import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Department;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ApplicationRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.DepartmentRepository;
import cv.igrp.platform.access_management.department.mapper.DepartmentMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * Command handler responsible for processing {@link PostDepartmentCommand} to create a new department.
 * <p>
 * This handler maps the incoming {@link DepartmentDTO} to a domain {@link Department} entity,
 * validates associated application and parent department references, and persists the new department.
 * </p>
 *
 * <p><strong>Validation behavior:</strong></p>
 * <ul>
 *   <li>If the provided {@code application_id} does not exist, throws {@link IgrpResponseStatusException} with HTTP 400.</li>
 *   <li>If the provided {@code parent_id} does not exist (when present), throws {@link IgrpResponseStatusException} with HTTP 400.</li>
 * </ul>
 *
 * <p><strong>Response:</strong> Returns HTTP 201 Created with the created {@link DepartmentDTO}.</p>
 *
 * @see DepartmentRepository
 * @see ApplicationRepository
 * @see DepartmentMapper
 * @see PostDepartmentCommand
 * @see DepartmentDTO
 */
@Service
public class PostDepartmentCommandHandler implements CommandHandler<PostDepartmentCommand, ResponseEntity<DepartmentDTO>> {

    private static final Logger logger =
            LoggerFactory.getLogger(PostDepartmentCommandHandler.class);

    private final DepartmentRepository departmentRepository;
    private final ApplicationRepository applicationRepository;
    private final DepartmentMapper departmentMapper;

    /**
     * Constructs the command handler with required dependencies.
     *
     * @param departmentRepository the repository used to persist departments
     * @param applicationRepository the repository used to fetch associated applications
     * @param departmentMapper the mapper used to convert between DTOs and domain entities
     */
    public PostDepartmentCommandHandler(
            DepartmentRepository departmentRepository,
            ApplicationRepository applicationRepository,
            DepartmentMapper departmentMapper) {
        this.departmentRepository = departmentRepository;
        this.applicationRepository = applicationRepository;
        this.departmentMapper = departmentMapper;
    }

    /**
     * Handles the creation of a department.
     * <p>
     * Validates and maps the incoming {@link DepartmentDTO}, resolves its application and optional parent,
     * persists the new department, and returns the corresponding {@link DepartmentDTO}.
     * </p>
     *
     * @param command the command containing the department creation request
     * @return HTTP 201 Created response with the created department DTO
     * @throws IgrpResponseStatusException if the application or parent department ID is invalid
     */
    @IgrpCommandHandler
    public ResponseEntity<DepartmentDTO> handle(PostDepartmentCommand command) {
        var departmentDto = command.getDepartmentdto();

        logger.info("Creating department: name={}, code={}", departmentDto.getName(), departmentDto.getCode());

        Department department = departmentMapper.toEntity(departmentDto);
        department.setApplicationId(applicationRepository.findById(command.getDepartmentdto().getApplication_id())
                .orElseThrow(() -> {
                    logger.warn("Invalid application ID: {}", departmentDto.getApplication_id());
                    return IgrpResponseStatusException.of(
                            HttpStatus.BAD_REQUEST, "Invalid application ID", null);
                }));

        if(departmentDto.getParent_id() != null) {
            Department parent = departmentRepository.findById(command.getDepartmentdto().getParent_id())
                    .orElseThrow(() -> {
                        logger.warn("Invalid parent ID: {}", departmentDto.getParent_id());
                        return IgrpResponseStatusException.of(
                                HttpStatus.BAD_REQUEST, "Invalid department ID", null);
                    });
            department.setParentId(parent);
        }

        Department saved = departmentRepository.save(department);

        logger.info("Department created successfully: id={}", saved.getId());

        DepartmentDTO result = departmentMapper.toDto(saved);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}