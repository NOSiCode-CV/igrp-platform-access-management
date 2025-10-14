package cv.igrp.platform.access_management.department.application.commands;

import cv.igrp.framework.auth.core.adapter.IAdapter;
import cv.igrp.framework.auth.core.exception.IAMException;
import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.department.mapper.DepartmentMapper;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
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
import org.springframework.transaction.annotation.Transactional;

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
   private final DepartmentMapper departmentMapper;
   private final IAdapter adapter;

   /**
    * Constructs the command handler with required dependencies.
    *
    * @param departmentRepository the repository used to persist departments
    * @param departmentMapper the mapper used to convert between DTOs and domain entities
    * @param adapter               the adapter interface used to interact with the external Identity and Access Management (IAM) system
    */
   public PostDepartmentCommandHandler(
           DepartmentEntityRepository departmentRepository,
           DepartmentMapper departmentMapper, IAdapter adapter) {
      this.departmentRepository = departmentRepository;
      this.departmentMapper = departmentMapper;
      this.adapter = adapter;
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
   @Transactional
   public ResponseEntity<DepartmentDTO> handle(PostDepartmentCommand command) {

      var departmentDto = command.getDepartmentdto();

      logger.info("Creating department: name={}, code={}", departmentDto.getName(), departmentDto.getCode());

      // Validate another department with same code does not exist
      departmentRepository.findByCodeAndStatusNot(departmentDto.getCode(), DepartmentStatus.DELETED)
              .ifPresent(_ -> {
                 logger.warn("Department code already exists: {}", departmentDto.getCode());
                 throw IgrpResponseStatusException.of(
                         HttpStatus.BAD_REQUEST,
                         "Department code already exists",
                         "Another department with code '" + departmentDto.getCode() + "' already exists.");
              });

      DepartmentEntity department = departmentMapper.toEntity(departmentDto);

      if(departmentDto.getParent_code() != null && !departmentDto.getParent_code().isBlank()) {
         DepartmentEntity parent = departmentRepository.findByCodeAndStatusNot(command.getDepartmentdto().getParent_code(), DepartmentStatus.DELETED)
                 .orElseThrow(() -> {
                    logger.warn("Invalid parent Code: {}", departmentDto.getParent_code());
                    return IgrpResponseStatusException.of(
                            HttpStatus.BAD_REQUEST, "Invalid department Code", "No parent department found with Code: " + departmentDto.getParent_code());
                 });
         department.setParentId(parent);
      }

      DepartmentEntity saved = departmentRepository.save(department);
      try {
        adapter.createDepartment(department.getCode(), department.getParentId() != null ? department.getParentId().getCode() : null);
      } catch (IAMException e) {
         throw IgrpResponseStatusException.of(
                 HttpStatus.INTERNAL_SERVER_ERROR,
                 "Department Creation Failed",
                 e.getMessage()
         );
      }

      logger.info("Department created successfully: code={}", saved.getCode());

      DepartmentDTO result = departmentMapper.toDto(saved);
      return ResponseEntity.status(HttpStatus.CREATED).body(result);
   }

}