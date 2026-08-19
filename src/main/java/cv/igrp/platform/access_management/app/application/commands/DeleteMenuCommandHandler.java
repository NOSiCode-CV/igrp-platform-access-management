package cv.igrp.platform.access_management.app.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.events.DepartmentScopeChangedEvent;
import cv.igrp.platform.access_management.shared.domain.events.EventPublisher;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;


@Component
public class DeleteMenuCommandHandler implements CommandHandler<DeleteMenuCommand, ResponseEntity<String>> {

   private final MenuEntryEntityRepository menuEntryRepository;
   private final ApplicationEntityRepository applicationRepository;
   private final EventPublisher eventPublisher;
   private final Logger logger = LoggerFactory.getLogger(DeleteMenuCommandHandler.class);

   /**
    * Constructs a new {@code DeleteMenuCommandHandler} with the required dependencies.
    *
    * @param menuEntryRepository the repository used to access and update {@link MenuEntryEntity} records
    * @param applicationRepository the repository used to access and update {@link ApplicationEntity} records
    * @param eventPublisher publishes per-department scope-change events so sessions are invalidated for users
    *                       whose roles referenced the deleted menu
    */
   public DeleteMenuCommandHandler(MenuEntryEntityRepository menuEntryRepository,
                                   ApplicationEntityRepository applicationRepository,
                                   EventPublisher eventPublisher) {
      this.menuEntryRepository = menuEntryRepository;
      this.applicationRepository = applicationRepository;
      this.eventPublisher = eventPublisher;
   }

   /**
    * Handles the {@link DeleteMenuCommand} by performing a soft delete on the specified menu entry.
    * <p>
    * The menu entry is not physically deleted from the database. Instead, its status is updated
    * to {@link Status#DELETED}, allowing it to be ignored or filtered out in application logic.
    * As part of the delete, this handler ALSO clears the menu from every department and every
    * role that referenced it (the owning side of {@code t_department_menuentry} and the menu's
    * {@code roles} collection), then publishes
    * {@link DepartmentScopeChangedEvent}({@link DepartmentScopeChangedEvent#CHANGE_MENUS}) per
    * affected department so users' JWT {@code menus} claims refresh on their next authenticated
    * request.
    * </p>
    *
    * @param command the command containing the ID of the menu to delete
    * @return {@link ResponseEntity} with HTTP 204 No Content if deletion is successful
    * @throws IgrpResponseStatusException if the menu entry is not found
    */
   @IgrpCommandHandler
   @Transactional
   public ResponseEntity<String> handle(DeleteMenuCommand command) {

      var appCode = command.getApplicationCode();

      var application = applicationRepository.findByCodeAndStatusNot(appCode, Status.DELETED)
              .orElseThrow(() -> {
                 logger.warn("Application with code {} not found", appCode);
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND, "Application not found", "Application not found with code: " + appCode);
              });

      MenuEntryEntity menuEntry = menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(application, command.getMenuCode(), Status.DELETED)
              .orElseThrow(() -> {
                 logger.warn("Menu entry with code {} not found", command.getMenuCode());
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND, "Menu not found", "Menu not found with code: " + command.getMenuCode());
              });

      Set<String> touchedDeptCodes = new HashSet<>();

      deleteChildMenus(menuEntry, touchedDeptCodes);

      // Fix (P1 gap): the menu used to be marked DELETED without touching its departments
      // or roles. Roles kept granting a deleted menu via t_menuentry_role, and
      // dept.getMenuentries() still surfaced it — so JWT menus/resource_access claims for
      // users assigned to those roles kept the deleted menu until token refresh. Clear
      // both sides and fire per-dept scope-change events so sessions are invalidated.
      if (menuEntry.getDepartments() != null) {
         for (DepartmentEntity dept : menuEntry.getDepartments()) {
            touchedDeptCodes.add(dept.getCode());
         }
         menuEntry.getDepartments().clear();
      }
      if (menuEntry.getRoles() != null) {
         menuEntry.getRoles().forEach(role -> {
            if (role.getDepartment() != null) touchedDeptCodes.add(role.getDepartment().getCode());
         });
         menuEntry.getRoles().clear();
      }

      menuEntry.setCode(menuEntry.getCode() + "-" + UUID.randomUUID());
      menuEntry.setStatus(Status.DELETED);

      var deletedMenuEntry = menuEntryRepository.save(menuEntry);
      logger.info("""
                    Menu with code={}, name={}, type={} has been marked as deleted
                    """,
              deletedMenuEntry.getCode(),
              deletedMenuEntry.getName(),
              deletedMenuEntry.getType());

      for (String deptCode : touchedDeptCodes) {
         eventPublisher.publishDepartmentScopeChanged(new DepartmentScopeChangedEvent(
                 deptCode, DepartmentScopeChangedEvent.CHANGE_MENUS, null));
      }

      return ResponseEntity.noContent().build();
   }

   private void deleteChildMenus(MenuEntryEntity menuEntry, Set<String> touchedDeptCodes) {

       if(menuEntry == null) return;

       var children = menuEntryRepository.findByParentId(menuEntry);

       if(children.isEmpty()) return;

       for (var child :  children) {

           if(child.getStatus() == Status.DELETED) continue;

           deleteChildMenus(child, touchedDeptCodes);

           // Same cascade for child menus so their role/dept links are cleaned up and
           // their affected departments contribute to the invalidation event batch.
           if (child.getDepartments() != null) {
              for (DepartmentEntity dept : child.getDepartments()) {
                 touchedDeptCodes.add(dept.getCode());
              }
              child.getDepartments().clear();
           }
           if (child.getRoles() != null) {
              child.getRoles().forEach(role -> {
                 if (role.getDepartment() != null) touchedDeptCodes.add(role.getDepartment().getCode());
              });
              child.getRoles().clear();
           }

           child.setCode(menuEntry.getCode() + "-" + UUID.randomUUID());
           child.setStatus(Status.DELETED);

           menuEntryRepository.save(child);

       }

   }

}
