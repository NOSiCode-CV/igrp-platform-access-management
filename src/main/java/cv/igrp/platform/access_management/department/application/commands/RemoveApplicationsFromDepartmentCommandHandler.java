package cv.igrp.platform.access_management.department.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


@Component
public class RemoveApplicationsFromDepartmentCommandHandler implements CommandHandler<RemoveApplicationsFromDepartmentCommand, ResponseEntity<String>> {

   private static final Logger LOGGER = LoggerFactory.getLogger(RemoveApplicationsFromDepartmentCommandHandler.class);

   private final ApplicationEntityRepository applicationRepository;
   private final DepartmentEntityRepository departmentRepository;

   public RemoveApplicationsFromDepartmentCommandHandler(ApplicationEntityRepository applicationRepository, DepartmentEntityRepository departmentRepository) {
      this.applicationRepository = applicationRepository;
      this.departmentRepository = departmentRepository;
   }

   @IgrpCommandHandler
   public ResponseEntity<String> handle(RemoveApplicationsFromDepartmentCommand command) {

      LOGGER.info("Handling applications removal from department: {}", command.getCode());

      var department = departmentRepository.findByCodeAndStatusNotDeleted(command.getCode());

      for (var appCode : command.getRemoveApplicationsFromDepartmentRequest()) {

         var app = applicationRepository.findByCodeAndStatusNotDeleted(appCode);

         department.getApplications().remove(app);

         departmentRepository.save(department);

         removeApplicationsForChildren(department, command.getRemoveApplicationsFromDepartmentRequest());

         LOGGER.info("Application <{}> was removed from department <{}> successfully", appCode, command.getCode());

      }

      return ResponseEntity.noContent().build();

   }

   private void removeApplicationsForChildren(DepartmentEntity department, List<String> appCodes) {
      if(!department.getChildrenids().isEmpty()) {

         for (var child : department.getChildrenids()) {

            var childDepartment = departmentRepository.findByCodeAndStatusNotDeleted(child.getCode());

            for (var appCode : appCodes) {

               var app = applicationRepository.findByCodeAndStatusNotDeleted(appCode);

               childDepartment.getApplications().remove(app);

               departmentRepository.save(childDepartment);

               removeApplicationsForChildren(childDepartment, appCodes);

               LOGGER.info("Application <{}> was removed from child department <{}> successfully", appCode, child);

            }

         }
      }
   }

}