package cv.igrp.platform.access_management.department.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.department.mapper.DepartmentMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;

/**
 * Command handler responsible for processing {@link PostDepartmentCommand} to create a new department.
 * <p>
 * This handler maps the incoming {@link DepartmentDTO} to a domain {@link DepartmentEntity} entity,
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
 * @see DepartmentEntityRepository
 * @see ApplicationEntityRepository
 * @see DepartmentMapper
 * @see PostDepartmentCommand
 * @see DepartmentDTO
 */
@Component
public class PostDepartmentCommandHandler implements CommandHandler<PostDepartmentCommand, ResponseEntity<DepartmentDTO>> {

   private static final Logger logger =
           LoggerFactory.getLogger(PostDepartmentCommandHandler.class);

   private final DepartmentEntityRepository departmentRepository;
   private final ApplicationEntityRepository applicationRepository;
   private final DepartmentMapper departmentMapper;

   /**
    * Constructs the command handler with required dependencies.
    *
    * @param departmentRepository the repository used to persist departments
    * @param applicationRepository the repository used to fetch associated applications
    * @param departmentMapper the mapper used to convert between DTOs and domain entities
    */
   public PostDepartmentCommandHandler(
           DepartmentEntityRepository departmentRepository,
           ApplicationEntityRepository applicationRepository,
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

      DepartmentEntity department = departmentMapper.toEntity(departmentDto);

      if(departmentDto.getParent_id() != null) {
         DepartmentEntity parent = departmentRepository.findById(command.getDepartmentdto().getParent_id())
                 .orElseThrow(() -> {
                    logger.warn("Invalid parent ID: {}", departmentDto.getParent_id());
                    return IgrpResponseStatusException.of(
                            HttpStatus.BAD_REQUEST, "Invalid department ID", "No parent department found with ID: " + departmentDto.getParent_id());
                 });
         department.setParentId(parent);
      }

      DepartmentEntity saved = departmentRepository.save(department);

      logger.info("Department created successfully: id={}", saved.getId());

      DepartmentDTO result = departmentMapper.toDto(saved);
      return ResponseEntity.status(HttpStatus.CREATED).body(result);
   }

}