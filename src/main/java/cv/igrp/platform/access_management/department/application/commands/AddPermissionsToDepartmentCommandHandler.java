package cv.igrp.platform.access_management.department.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
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

   public AddPermissionsToDepartmentCommandHandler(DepartmentEntityRepository departmentRepository, PermissionEntityRepository permissionRepository) {
      this.departmentRepository = departmentRepository;
      this.permissionRepository = permissionRepository;
   }

   @IgrpCommandHandler
   @Transactional
   public ResponseEntity<String> handle(AddPermissionsToDepartmentCommand command) {

      var department = departmentRepository.findByCodeAndStatusNotDeleted(command.getCode());

      for (var permissionName : command.getAddPermissionsToDepartmentRequest()) {

         var permission = permissionRepository.findByNameAndStatusNotDeleted(permissionName);

         permission.getDepartments().add(department);

         permissionRepository.save(permission);

      }

      return ResponseEntity.noContent().build();

   }

}