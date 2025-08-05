package cv.igrp.platform.access_management.department.application.commands;

import cv.igrp.framework.auth.core.adapter.IAdapter;
import cv.igrp.framework.auth.core.exception.IAMException;
import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.department.mapper.DepartmentMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;
import org.springframework.transaction.annotation.Transactional;

/**
 * Command handler responsible for processing {@link UpdateDepartmentCommand} to update an existing department.
 * <p>
 * It performs the following steps:
 * <ul>
 *     <li>Fetches the department by ID from the {@link DepartmentEntityRepository}.</li>
 *     <li>If not found, throws {@link IgrpResponseStatusException}.</li>
 *     <li>Updates the existing {@link DepartmentEntity} entity with values from the {@link DepartmentDTO} using {@link DepartmentMapper}.</li>
 *     <li>Saves the updated entity back to the repository.</li>
 *     <li>Returns the updated department as a {@link DepartmentDTO} in an HTTP 200 OK response.</li>
 * </ul>
 *
 */
@Component
public class UpdateDepartmentCommandHandler implements CommandHandler<UpdateDepartmentCommand, ResponseEntity<DepartmentDTO>> {

   private static final Logger logger =
           LoggerFactory.getLogger(UpdateDepartmentCommandHandler.class);

   private final DepartmentEntityRepository departmentRepository;
   private final DepartmentMapper departmentMapper;
   private final IAdapter adapter;

   /**
    * Constructs the command handler with required dependencies.
    *
    * @param departmentRepository the repository used to retrieve and save department entities
    * @param departmentMapper the mapper used to convert between DTOs and entities
    * @param adapter               the adapter interface used to interact with the external Identity and Access Management (IAM) system
    */
   public UpdateDepartmentCommandHandler(
           DepartmentEntityRepository departmentRepository, DepartmentMapper departmentMapper, IAdapter adapter) {
      this.departmentRepository = departmentRepository;
      this.departmentMapper = departmentMapper;
      this.adapter = adapter;
   }

   /**
    * Handles the update of an existing department.
    *
    * @param command the update command containing the department ID and updated data
    * @return a {@link ResponseEntity} with status 200 OK and the updated department DTO
    * @throws IgrpResponseStatusException if no department is found with the given ID
    */
   @IgrpCommandHandler
   @Transactional
   public ResponseEntity<DepartmentDTO> handle(UpdateDepartmentCommand command) {
      String departmentCode = command.getCode();

      logger.info("Updating department with code {}", departmentCode);

      DepartmentEntity department = departmentRepository.findByCode(departmentCode)
              .orElseThrow(() -> {
                 logger.warn("Department with code={} not found", departmentCode);
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND, "Invalid Department Code", "Department not found with code: " + departmentCode);
              });

      departmentMapper.updateEntityFromDto(command.getDepartmentdto(), department);

      DepartmentEntity updated = departmentRepository.save(department);

      if (!departmentCode.equals(department.getCode())) {
          try {
              adapter.updateDepartment(departmentCode, department.getCode());
          } catch (IAMException e) {
              throw new RuntimeException(e);
          }
      }

      logger.info("Successfully updated department with code={}", updated.getCode());

      return ResponseEntity.ok(departmentMapper.toDto(updated));
   }

}