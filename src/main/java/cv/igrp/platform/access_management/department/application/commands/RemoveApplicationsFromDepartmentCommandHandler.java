package cv.igrp.platform.access_management.department.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.domain.events.DepartmentScopeChangedEvent;
import cv.igrp.platform.access_management.shared.domain.events.EventPublisher;
import cv.igrp.platform.access_management.shared.infrastructure.service.ScopeService;
import cv.igrp.platform.access_management.security_audit.domain.events.ApplicationDisassociatedFromRoleEvent;
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
public class RemoveApplicationsFromDepartmentCommandHandler implements CommandHandler<RemoveApplicationsFromDepartmentCommand, ResponseEntity<String>> {

   private static final Logger LOGGER = LoggerFactory.getLogger(RemoveApplicationsFromDepartmentCommandHandler.class);

   private final ApplicationEntityRepository applicationRepository;
   private final DepartmentEntityRepository departmentRepository;
   private final EventPublisher eventPublisher;
   private final ScopeService scopeService;

   public RemoveApplicationsFromDepartmentCommandHandler(ApplicationEntityRepository applicationRepository, DepartmentEntityRepository departmentRepository, EventPublisher eventPublisher, ScopeService scopeService) {
      this.applicationRepository = applicationRepository;
      this.departmentRepository = departmentRepository;
      this.eventPublisher = eventPublisher;
      this.scopeService = scopeService;
   }

   @Transactional
   @IgrpCommandHandler
   public ResponseEntity<String> handle(RemoveApplicationsFromDepartmentCommand command) {

      LOGGER.info("Handling applications removal from department: {}", command.getCode());

      var department = departmentRepository.findByCodeAndStatusNotDeleted(command.getCode());

      // Scope enforcement (R1.2): the target department must be in the caller's scope.
      scopeService.assertInScope(department.getId());

      for (var appCode : command.getRemoveApplicationsFromDepartmentRequest()) {

         var app = applicationRepository.findByCodeAndStatusNotDeleted(appCode);

         department.getApplications().remove(app);

         departmentRepository.save(department);

         applicationRepository.save(app);

         eventPublisher.publishSettingsAudit(
                 new ApplicationDisassociatedFromRoleEvent(app.getName(), department.getCode()));

         removeApplicationsForChildren(department, command.getRemoveApplicationsFromDepartmentRequest());

         LOGGER.info("Application <{}> was removed from department <{}> successfully", appCode, command.getCode());

      }

      // Cascade to roles across the entire subtree. A child department can only have an
      // application granted to one of its roles if some ancestor made it available, so
      // removing the application from the subtree's departments must also strip it from
      // every role in that subtree — otherwise those roles keep granting an application
      // the department no longer exposes.
      Set<DepartmentEntity> subtree = new LinkedHashSet<>();
      collectActiveSubtree(department, subtree);
      Set<Integer> subtreeIds = subtree.stream().map(DepartmentEntity::getId).collect(Collectors.toSet());
      scrubApplicationsFromRolesInSubtree(command.getRemoveApplicationsFromDepartmentRequest(), subtreeIds);

      return ResponseEntity.noContent().build();

   }

   /**
    * For each application code, drops every role whose department falls in {@code subtreeIds}
    * from {@link ApplicationEntity#getRoles()} (the owning side of the app↔role join, since
    * {@link RoleEntity#getApplications()} is {@code mappedBy}). Fires a per-department
    * {@link DepartmentScopeChangedEvent}({@code CHANGE_APPLICATIONS}) so the
    * session-invalidation listener refreshes tokens for users in each touched department.
    */
   private void scrubApplicationsFromRolesInSubtree(List<String> appCodes, Set<Integer> subtreeIds) {
      if (subtreeIds.isEmpty()) return;
      Set<String> touchedDeptCodes = new HashSet<>();
      for (var appCode : appCodes) {
         var app = applicationRepository.findByCodeAndStatusNotDeleted(appCode);
         boolean modified = false;
         Iterator<RoleEntity> it = app.getRoles().iterator();
         while (it.hasNext()) {
            RoleEntity role = it.next();
            var roleDept = role.getDepartment();
            if (roleDept != null && subtreeIds.contains(roleDept.getId())) {
               it.remove();
               modified = true;
               touchedDeptCodes.add(roleDept.getCode());
               LOGGER.info("Cascade: removed application '{}' from role '{}' in department '{}'",
                       appCode, role.getCode(), roleDept.getCode());
            }
         }
         if (modified) {
            applicationRepository.save(app);
         }
      }
      for (var code : touchedDeptCodes) {
         eventPublisher.publishDepartmentScopeChanged(new DepartmentScopeChangedEvent(
                 code, DepartmentScopeChangedEvent.CHANGE_APPLICATIONS, null));
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

   private void removeApplicationsForChildren(DepartmentEntity department, List<String> appCodes) {
      if(!department.getChildrenids().isEmpty()) {

         for (var child : department.getChildrenids()) {

            var childDepartment = departmentRepository.findByCodeAndStatusNotDeleted(child.getCode());

            for (var appCode : appCodes) {

               var app = applicationRepository.findByCodeAndStatusNotDeleted(appCode);

               childDepartment.getApplications().remove(app);

               departmentRepository.save(childDepartment);

               applicationRepository.save(app);

               removeApplicationsForChildren(childDepartment, appCodes);

               LOGGER.info("Application <{}> was removed from child department <{}> successfully", appCode, child.getCode());

            }

         }
      }
   }

}
