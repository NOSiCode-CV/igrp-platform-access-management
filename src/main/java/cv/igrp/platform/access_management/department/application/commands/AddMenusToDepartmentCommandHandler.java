package cv.igrp.platform.access_management.department.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


@Component
public class AddMenusToDepartmentCommandHandler implements CommandHandler<AddMenusToDepartmentCommand, ResponseEntity<String>> {

   private static final Logger LOGGER = LoggerFactory.getLogger(AddMenusToDepartmentCommandHandler.class);

   private final MenuEntryEntityRepository menuEntryRepository;
   private final DepartmentEntityRepository departmentRepository;
   
   public AddMenusToDepartmentCommandHandler(MenuEntryEntityRepository menuEntryRepository, DepartmentEntityRepository departmentRepository) {
      this.menuEntryRepository = menuEntryRepository;
      this.departmentRepository = departmentRepository;
   }

   @IgrpCommandHandler
   public ResponseEntity<String> handle(AddMenusToDepartmentCommand command) {
      List<String> menuCodes = command.getAddMenusToDepartmentRequest();
      var departmentOpt = departmentRepository.findByCodeAndStatusNot(command.getCode(), DepartmentStatus.DELETED);
      if (departmentOpt.isEmpty()) {
         LOGGER.warn("Department not found with code: {}", command.getCode());
         throw IgrpResponseStatusException.of(
                 HttpStatus.NOT_FOUND,
                 "Department not found",
                 "Department not found with code: " + command.getCode());
      }
      var department = departmentOpt.get();
      for (String menuCode : menuCodes) {
         var menuEntryOpt = menuEntryRepository.findByCodeAndStatusNot(menuCode, Status.DELETED);
         if (menuEntryOpt.isEmpty()) {
            LOGGER.warn("Menu Entry not found with code: {}", menuCode);
            throw IgrpResponseStatusException.of(
                    HttpStatus.NOT_FOUND,
                    "Menu Entry not found",
                    "Menu Entry not found with code: " + menuCode);
         }
         var menuEntry = menuEntryOpt.get();
         if (!menuEntry.getDepartments().contains(department)) {
            var optParentDepartment = department.getParentId();
            if(optParentDepartment != null) {

               var parentDepartment = departmentRepository.findById(optParentDepartment.getId()).orElseThrow(
                       () -> IgrpResponseStatusException.notFound("Parent Department was not found: " + optParentDepartment.getCode())
               );

               if(parentDepartment.getMenuentries().stream().map(MenuEntryEntity::getCode).toList().contains(menuCode)) {
                  menuEntry.getDepartments().add(department);
               } else {
                  LOGGER.warn("Cannot add menu <{}> to department <{}> because its parent department <{}> is not associated with the menu", menuCode, command.getCode(), department.getParentId().getCode());
                  throw IgrpResponseStatusException.of(
                          HttpStatus.BAD_REQUEST,
                          "Invalid Department Association",
                          "Cannot add menu " + menuCode + " to department " + command.getCode() + " because its parent department " + department.getParentId().getCode() + " is not associated with the menu");
               }
            } else {
               menuEntry.getDepartments().add(department);
            }
            menuEntryRepository.save(menuEntry);
            LOGGER.info("Added menu <{}> to department <{}>", menuCode, command.getCode());
         } else {
            LOGGER.info("Menu <{}> is already associated with department <{}>", menuCode, command.getCode());
         }
      }
      
      return ResponseEntity.noContent().build();
   }

}