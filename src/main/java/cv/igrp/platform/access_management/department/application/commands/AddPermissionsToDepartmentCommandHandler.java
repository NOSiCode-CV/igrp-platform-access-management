package cv.igrp.platform.access_management.department.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.service.ScopeService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;


@Component
public class AddPermissionsToDepartmentCommandHandler implements CommandHandler<AddPermissionsToDepartmentCommand, ResponseEntity<String>> {

   private static final Logger LOGGER = LoggerFactory.getLogger(AddPermissionsToDepartmentCommandHandler.class);

   private final DepartmentEntityRepository departmentRepository;
   private final PermissionEntityRepository permissionRepository;
   private final ScopeService scopeService;

   public AddPermissionsToDepartmentCommandHandler(DepartmentEntityRepository departmentRepository, PermissionEntityRepository permissionRepository, ScopeService scopeService) {
      this.departmentRepository = departmentRepository;
      this.permissionRepository = permissionRepository;
      this.scopeService = scopeService;
   }

   @IgrpCommandHandler
   @Transactional
   public ResponseEntity<String> handle(AddPermissionsToDepartmentCommand command) {

      var department = departmentRepository.findByCodeAndStatusNotDeleted(command.getCode());

      // Scope enforcement (R1.2): the target department must be in the caller's scope.
      scopeService.assertInScope(department.getId());

      for (var permissionName : command.getAddPermissionsToDepartmentRequest()) {

         var permission = permissionRepository.findByNameAndStatusNotDeleted(permissionName);

         permission.getDepartments().add(department);

         permissionRepository.save(permission);

      }

      return ResponseEntity.noContent().build();

   }

}