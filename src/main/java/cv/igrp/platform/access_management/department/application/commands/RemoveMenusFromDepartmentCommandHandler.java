package cv.igrp.platform.access_management.department.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import cv.igrp.platform.access_management.shared.domain.events.DepartmentScopeChangedEvent;
import cv.igrp.platform.access_management.shared.domain.events.EventPublisher;
import cv.igrp.platform.access_management.shared.infrastructure.service.ScopeService;
import cv.igrp.platform.access_management.security_audit.domain.events.MenuDisassociatedFromRoleEvent;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@Component
public class RemoveMenusFromDepartmentCommandHandler implements CommandHandler<RemoveMenusFromDepartmentCommand, ResponseEntity<String>> {

   private static final Logger LOGGER = LoggerFactory.getLogger(RemoveMenusFromDepartmentCommandHandler.class);

   private final MenuEntryEntityRepository menuEntryRepository;
   private final DepartmentEntityRepository departmentEntityRepository;
   private final ApplicationEntityRepository applicationEntityRepository;
   private final EventPublisher eventPublisher;
   private final ScopeService scopeService;

   public RemoveMenusFromDepartmentCommandHandler(MenuEntryEntityRepository menuEntryRepository, DepartmentEntityRepository departmentEntityRepository, ApplicationEntityRepository applicationEntityRepository, EventPublisher eventPublisher, ScopeService scopeService) {
      this.menuEntryRepository = menuEntryRepository;
      this.departmentEntityRepository = departmentEntityRepository;
      this.applicationEntityRepository = applicationEntityRepository;
      this.eventPublisher = eventPublisher;
      this.scopeService = scopeService;
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

      // Scope enforcement (R1.2): the target department must be in the caller's scope.
      scopeService.assertInScope(department.getId());

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
            eventPublisher.publishSettingsAudit(
                    new MenuDisassociatedFromRoleEvent(menuCode, department.getCode()));
         } else {
            LOGGER.info("Menu entry with code: {} not associated with department with code: {}.", menuCode, command.getDepartmentCode());
         }
      });

      removeMenusForChildren(application, department, command.getRemoveMenusFromDepartmentRequest());

      // Cascade to roles across the entire subtree. A child department can only have a menu
      // granted to one of its roles if some ancestor made it available, so removing the menu
      // from the subtree's departments must also strip it from every role in that subtree —
      // otherwise those roles keep granting a menu the department no longer exposes.
      Set<DepartmentEntity> subtree = new LinkedHashSet<>();
      collectActiveSubtree(department, subtree);
      Set<Integer> subtreeIds = subtree.stream().map(DepartmentEntity::getId).collect(Collectors.toSet());
      scrubMenusFromRolesInSubtree(application, menuIds, subtreeIds);

      return ResponseEntity.noContent().build();

   }

   /**
    * For each menu code, drops every role whose department falls in {@code subtreeIds} from
    * {@link MenuEntryEntity#getRoles()} (the owning side of the menu↔role join). Fires a
    * per-department {@link DepartmentScopeChangedEvent}({@code CHANGE_MENUS}) so the
    * session-invalidation listener refreshes tokens for users in each touched department.
    */
   private void scrubMenusFromRolesInSubtree(ApplicationEntity application, List<String> menuCodes, Set<Integer> subtreeIds) {
      if (subtreeIds.isEmpty()) return;
      Set<String> touchedDeptCodes = new HashSet<>();
      for (var menuCode : menuCodes) {
         var opt = menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(application, menuCode, Status.DELETED);
         if (opt.isEmpty()) continue;
         MenuEntryEntity menuEntry = opt.get();
         boolean modified = false;
         Iterator<RoleEntity> it = menuEntry.getRoles().iterator();
         while (it.hasNext()) {
            RoleEntity role = it.next();
            var roleDept = role.getDepartment();
            if (roleDept != null && subtreeIds.contains(roleDept.getId())) {
               it.remove();
               modified = true;
               touchedDeptCodes.add(roleDept.getCode());
               LOGGER.info("Cascade: removed menu '{}' from role '{}' in department '{}'",
                       menuCode, role.getCode(), roleDept.getCode());
            }
         }
         if (modified) {
            menuEntryRepository.save(menuEntry);
         }
      }
      for (var code : touchedDeptCodes) {
         eventPublisher.publishDepartmentScopeChanged(new DepartmentScopeChangedEvent(
                 code, DepartmentScopeChangedEvent.CHANGE_MENUS, null));
      }
   }

   /**
    * Depth-first walk of {@code dept} and its non-deleted descendants via
    * {@link DepartmentEntity#getChildrenids()}.
    */
   private void collectActiveSubtree(DepartmentEntity dept, Set<DepartmentEntity> acc) {
      if (dept == null || dept.getStatus() == DepartmentStatus.DELETED) return;
      if (!acc.add(dept)) return;
      if (dept.getChildrenids() != null) {
         for (var child : dept.getChildrenids()) {
            collectActiveSubtree(child, acc);
         }
      }
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
