package cv.igrp.platform.access_management.department.application.commands;

import cv.igrp.framework.auth.core.adapter.IAdapter;
import cv.igrp.framework.auth.core.exception.IAMException;
import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.role.domain.service.RoleValidator;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.validation.ResourceValidationResponse;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import org.springframework.transaction.annotation.Transactional;

/**
 * Command handler responsible for processing the {@link CreateRoleCommand}
 * to create a new role in the system.
 * <p>
 * This handler ensures that the specified department exists and, if provided, that the parent role exists
 * and is not marked as deleted. It maps the input data to the corresponding entity, persists the new role,
 * and returns its DTO representation.
 * @see CreateRoleCommand
 * @see RoleDTO
 * @see RoleEntity
 * @see RoleEntityRepository
 * @see RoleMapper
 * @see DepartmentEntity
 * @see DepartmentEntityRepository
 * @see Status
 * @see IgrpResponseStatusException
 */
@Slf4j
@Component
public class CreateRoleCommandHandler implements CommandHandler<CreateRoleCommand, ResponseEntity<RoleDTO>> {

   private final DepartmentEntityRepository departmentRepository;
   private final RoleEntityRepository roleRepository;
   private final RoleMapper roleMapper;
   private final IAdapter adapter;

   /**
    * Constructs the role creation handler with required dependencies.
    *
    * @param departmentRepository repository to access department data
    * @param roleRepository repository to manage roles
    * @param roleMapper mapper to convert between entity and DTO
    * @param adapter the adapter interface used to interact with the external Identity and Access Management (IAM) system
    */
   public CreateRoleCommandHandler(DepartmentEntityRepository departmentRepository, RoleEntityRepository roleRepository, RoleMapper roleMapper, IAdapter adapter) {
      this.departmentRepository = departmentRepository;
      this.roleRepository = roleRepository;
      this.roleMapper = roleMapper;
      this.adapter = adapter;
   }

   /**
    * Handles the creation of a new role using the data provided in the {@link CreateRoleCommand}.
    * <ul>
    *     <li>Validates the existence of the associated department.</li>
    *     <li>If a parent role ID is provided, validates its existence and status.</li>
    *     <li>Maps the DTO to a Role entity, persists it, and returns the result as a DTO.</li>
    * </ul>
    *
    * @param command the command containing the role data to be created
    * @return a {@link ResponseEntity} containing the created {@link RoleDTO}
    * @throws IgrpResponseStatusException if the department or parent role is not found
    */
   @IgrpCommandHandler
   @Transactional
   public ResponseEntity<RoleDTO> handle(CreateRoleCommand command) {
      RoleDTO request = command.getRoledto();
      RoleEntity parentRole = null;
      DepartmentEntity department = departmentRepository.findByCodeAndStatusNot(command.getCode(), DepartmentStatus.DELETED)
              .orElseThrow(() -> {
                 log.warn("Department with code: {} not found.", command.getCode());
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND, "Create Role", "Department with code: " + command.getCode() + " not found."
                 );
              });

      String roleCode = RoleValidator.normalizeRoleCode(command.getRoledto().getCode(), command.getRoledto().getParentCode());

      command.getRoledto().setCode(roleCode);

      log.info("Create Role with code: {}.", command.getRoledto().getCode());

      ResourceValidationResponse roleValidationResponse = RoleValidator.validateRoleDto(command.getRoledto(), department);
      if(!roleValidationResponse.isValid()){
         throw IgrpResponseStatusException.of(
                 HttpStatus.CONFLICT, "Create Role", roleValidationResponse.getFailureMessage()
         );
      }

      if (command.getRoledto().getParentCode() != null) {
         String parentRoleCode = command.getRoledto().getParentCode();
         parentRole = roleRepository.findByDepartmentAndCodeAndStatusNot(department, parentRoleCode, Status.DELETED)
                 .orElseThrow(() -> {
                    log.warn("Parent Role with code: {} not found.", command.getRoledto().getParentCode());
                    return IgrpResponseStatusException.of(
                            HttpStatus.NOT_FOUND, "Create Role", "Parent Role with code: " + parentRoleCode + " not found."
                    );
                 });
      }

      RoleEntity newRole = roleMapper.mapToEntity(request, department, parentRole);
      RoleEntity savedRole = roleRepository.save(newRole);

      try {
         adapter.createRole(department.getCode(), RoleValidator.normalizeRoleCodeForAdapter(department.getCode(), savedRole.getCode()));
      } catch (IAMException e) {
         throw IgrpResponseStatusException.of(
                 HttpStatus.INTERNAL_SERVER_ERROR,
                 "Role Creation Failed",
                 e.getMessage()
         );
      }

      RoleDTO roleDTO = roleMapper.mapToDto(savedRole);
      log.info("Role with code: {} created successfully.", command.getRoledto().getCode());
      return new ResponseEntity<>(roleDTO, HttpStatus.CREATED);
   }

}