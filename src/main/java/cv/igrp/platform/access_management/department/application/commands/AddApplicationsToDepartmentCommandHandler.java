package cv.igrp.platform.access_management.department.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



@Component
public class AddApplicationsToDepartmentCommandHandler implements CommandHandler<AddApplicationsToDepartmentCommand, ResponseEntity<String>> {

   private static final Logger LOGGER = LoggerFactory.getLogger(AddApplicationsToDepartmentCommandHandler.class);

   private final ApplicationEntityRepository applicationRepository;
   private final DepartmentEntityRepository departmentRepository;

   public AddApplicationsToDepartmentCommandHandler(ApplicationEntityRepository applicationRepository, DepartmentEntityRepository departmentRepository) {
      this.applicationRepository = applicationRepository;
      this.departmentRepository = departmentRepository;
   }

   @IgrpCommandHandler
   public ResponseEntity<String> handle(AddApplicationsToDepartmentCommand command) {

      LOGGER.info("Handling add applications to department: {}", command.getCode());

      var department = departmentRepository.findByCodeAndStatusNotDeleted(command.getCode());

      for (var applicationCode : command.getAddApplicationsToDepartmentRequest()) {

         var application = applicationRepository.findByCodeAndStatusNotDeleted(applicationCode);

         var optParentDepartment = department.getParentId();

         if (optParentDepartment != null) {
            var parentDepartment = departmentRepository.findById(optParentDepartment.getId()).orElseThrow(
                    () -> IgrpResponseStatusException.notFound("Parent Department was not found: " + optParentDepartment.getCode())
            );
            if (!parentDepartment.getApplications().stream().map(ApplicationEntity::getCode).toList().contains(application.getCode()))
               throw IgrpResponseStatusException.forbidden(
                       "Cannot associate department '%s' because its parent department '%s' is not assigned to the application '%s'".formatted(
                               department.getCode(),
                               parentDepartment.getCode(),
                               application.getCode()
                       )
               );
         }

         department.getApplications().add(application);

         departmentRepository.save(department);

      }

      return ResponseEntity.noContent().build();

   }

}