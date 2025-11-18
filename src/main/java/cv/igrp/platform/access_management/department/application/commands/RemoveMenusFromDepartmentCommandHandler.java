package cv.igrp.platform.access_management.department.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Component
public class RemoveMenusFromDepartmentCommandHandler implements CommandHandler<RemoveMenusFromDepartmentCommand, ResponseEntity<String>> {

   private static final Logger LOGGER = LoggerFactory.getLogger(RemoveMenusFromDepartmentCommandHandler.class);

   private final MenuEntryEntityRepository menuEntryRepository;
   private final DepartmentEntityRepository departmentEntityRepository;
   private final ApplicationEntityRepository applicationEntityRepository;

   public RemoveMenusFromDepartmentCommandHandler(MenuEntryEntityRepository menuEntryRepository, DepartmentEntityRepository departmentEntityRepository, ApplicationEntityRepository applicationEntityRepository) {
      this.menuEntryRepository = menuEntryRepository;
      this.departmentEntityRepository = departmentEntityRepository;
      this.applicationEntityRepository = applicationEntityRepository;
   }

   @IgrpCommandHandler
   @Transactional
   public ResponseEntity<String> handle(RemoveMenusFromDepartmentCommand command) {

      List<String> menuIds = command.getRemoveMenusFromDepartmentRequest();
      String appCode = command.getApplicationCode();
      var department = departmentEntityRepository.findByCodeAndStatusNot(command.getDepartmentCode(), DepartmentStatus.DELETED)
              .orElseThrow(() -> {
                 LOGGER.warn("Department not found with code: {}", command.getDepartmentCode());
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND,
                         "Department not found",
                         "Department not found with code: " + command.getDepartmentCode());
              });
      var application = applicationEntityRepository.findByCodeAndStatusNot(appCode, Status.DELETED)
              .orElseThrow(() -> {
                 LOGGER.warn("Application not found with code: {}", appCode);
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND,
                         "Application not found",
                         "Application not found with code: " + appCode);
              });
      menuIds.forEach(menuCode -> {
         var menuEntry = menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(application, menuCode, Status.DELETED).orElseThrow(() -> {
            LOGGER.warn("Menu Entry not found with code: <{}> for application with code: <{}>", menuCode, appCode);
            return IgrpResponseStatusException.of(
                    HttpStatus.NOT_FOUND,
                    "Menu Entry not found",
                    "Menu Entry not found with code: " + menuCode);
         });
         if (menuEntry.getDepartments().stream().map(DepartmentEntity::getCode).toList().contains(department.getCode())) {
            menuEntry.getDepartments().remove(department);
            //removeDepartmentFromParents(menuEntry, department);
            LOGGER.info("Menu entry with code: {} removed from department with code: {}.", menuCode, command.getDepartmentCode());
            menuEntryRepository.save(menuEntry);
         } else {
            LOGGER.info("Menu entry with code: {} not associated with department with code: {}.", menuCode, command.getDepartmentCode());
         }
      });

      removeMenusForChildren(application, department, command.getRemoveMenusFromDepartmentRequest());

      return ResponseEntity.noContent().build();

   }

   private void removeMenusForChildren(ApplicationEntity application, DepartmentEntity department, List<String> menuCodes) {

      if(!department.getChildrenids().isEmpty()) {

         for (var child : department.getChildrenids()) {

            var childDepartment = departmentEntityRepository.findByCodeAndStatusNotDeleted(child.getCode());

            for (var menuCode : menuCodes) {

               var menuEntry = menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(application, menuCode, Status.DELETED).orElseThrow(() -> {
                  LOGGER.warn("Menu Entry not found with code: <{}>", menuCode);
                  return IgrpResponseStatusException.of(
                          HttpStatus.NOT_FOUND,
                          "Menu Entry not found",
                          "Menu Entry not found with code: " + menuCode);
               });

               if (menuEntry.getDepartments().stream().map(DepartmentEntity::getCode).toList().contains(childDepartment.getCode())) {
                  menuEntry.getDepartments().remove(childDepartment);
                  if(menuEntry.getParentId() != null) {
                     var parentMenuEntry = menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(application, menuEntry.getParentId().getCode(), Status.DELETED).orElseThrow(
                             () -> IgrpResponseStatusException.of(
                                     HttpStatus.NOT_FOUND,
                                     "Parent Menu Entry not found",
                                     "Parent Menu Entry not found with code: " + menuEntry.getParentId().getCode())
                     );
                     parentMenuEntry.getDepartments().remove(childDepartment);
                     menuEntryRepository.save(parentMenuEntry);
                  }
                  LOGGER.info("Menu entry with code: {} removed from child department with code: {}.", menuCode, childDepartment.getCode());
                  menuEntryRepository.save(menuEntry);
               } else {
                  LOGGER.info("Menu entry with code: {} not associated with child department with code: {}.", menuCode, childDepartment.getCode());
               }

               removeMenusForChildren(application, childDepartment, menuCodes);

            }

         }

      }

   }

   private void removeDepartmentFromParents(ApplicationEntity application, MenuEntryEntity menuEntry, DepartmentEntity department) {

      if(menuEntry.getParentId() != null) {
         var parentMenuEntry = menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(application, menuEntry.getParentId().getCode(), Status.DELETED).orElseThrow(
                 () -> IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND,
                         "Parent Menu Entry not found",
                         "Parent Menu Entry not found with code: " + menuEntry.getParentId().getCode())
         );
         parentMenuEntry.getDepartments().remove(department);

         removeDepartmentFromParents(application, parentMenuEntry, department);

         menuEntryRepository.save(parentMenuEntry);
      }

   }

}