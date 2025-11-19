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
public class RemovePermissionsFromDepartmentCommandHandler implements CommandHandler<RemovePermissionsFromDepartmentCommand, ResponseEntity<String>> {

   private static final Logger LOGGER = LoggerFactory.getLogger(RemovePermissionsFromDepartmentCommandHandler.class);

   private final DepartmentEntityRepository departmentRepository;
   private final PermissionEntityRepository permissionRepository;

   public RemovePermissionsFromDepartmentCommandHandler(DepartmentEntityRepository departmentRepository, PermissionEntityRepository permissionRepository) {
      this.departmentRepository = departmentRepository;
      this.permissionRepository = permissionRepository;
   }

   @IgrpCommandHandler
   @Transactional
   public ResponseEntity<String> handle(RemovePermissionsFromDepartmentCommand command) {

      var department = departmentRepository.findByCodeAndStatusNotDeleted(command.getCode());

      for (var permissionCode : command.getRemovePermissionsFromDepartmentRequest()) {
         var permission = permissionRepository.findByNameAndStatusNotDeleted(permissionCode);
         permission.getDepartments().remove(department);
         permissionRepository.save(permission);
      }

      return ResponseEntity.noContent().build();

   }

}