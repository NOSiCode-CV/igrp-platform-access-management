package cv.igrp.platform.access_management.department.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.session.domain.event.RolePermissionChangedEvent;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.domain.events.DepartmentScopeChangedEvent;
import cv.igrp.platform.access_management.shared.domain.events.EventPublisher;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.service.ScopeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;


@Component
public class RemovePermissionsFromDepartmentCommandHandler implements CommandHandler<RemovePermissionsFromDepartmentCommand, ResponseEntity<String>> {

   private static final Logger LOGGER = LoggerFactory.getLogger(RemovePermissionsFromDepartmentCommandHandler.class);

   private final DepartmentEntityRepository departmentRepository;
   private final PermissionEntityRepository permissionRepository;
   private final RoleEntityRepository roleRepository;
   private final EventPublisher eventPublisher;
   private final ScopeService scopeService;

   public RemovePermissionsFromDepartmentCommandHandler(DepartmentEntityRepository departmentRepository,
                                                        PermissionEntityRepository permissionRepository,
                                                        RoleEntityRepository roleRepository,
                                                        EventPublisher eventPublisher,
                                                        ScopeService scopeService) {
      this.departmentRepository = departmentRepository;
      this.permissionRepository = permissionRepository;
      this.roleRepository = roleRepository;
      this.eventPublisher = eventPublisher;
      this.scopeService = scopeService;
   }

   @IgrpCommandHandler
   @Transactional
   public ResponseEntity<String> handle(RemovePermissionsFromDepartmentCommand command) {

      var department = departmentRepository.findByCodeAndStatusNotDeleted(command.getCode());

      // Scope enforcement (R1.2): the target department must be in the caller's scope.
      scopeService.assertInScope(department.getId());

      // Cascade scope: the department itself + every non-deleted descendant. A child
      // department can only have a permission granted if some ancestor made it available,
      // so unlinking here must strip that availability from the whole subtree AND strip
      // it from every role in that subtree that already held it — otherwise those roles
      // (and their users' JWTs) keep granting a permission the department no longer offers.
      Set<DepartmentEntity> subtree = new LinkedHashSet<>();
      collectActiveSubtree(department, subtree);
      Set<Integer> subtreeIds = subtree.stream().map(DepartmentEntity::getId).collect(Collectors.toSet());
      Set<Integer> roleIdsInSubtree = roleRepository.findIdsByDepartmentIdIn(subtreeIds);

      for (var permissionCode : command.getRemovePermissionsFromDepartmentRequest()) {

         var permission = permissionRepository.findByNameAndStatusNotDeleted(permissionCode);

         // 1) Unlink permission ↔ every department in the subtree that held it.
         Set<DepartmentEntity> deptsTouched = new HashSet<>();
         for (var d : subtree) {
            if (permission.getDepartments().remove(d)) {
               deptsTouched.add(d);
               LOGGER.info("Removed permission '{}' from department '{}'", permissionCode, d.getCode());
            }
         }
         permissionRepository.save(permission);

         // 2) Strip the permission from every role in the subtree that had it granted.
         Set<String> rolesTouchedDeptCodes = scrubPermissionFromRoles(roleIdsInSubtree, permissionCode);

         // 3) Publish per-role invalidation events (mirrors RemovePermissionsCommandHandler
         //    which fires the same event when a permission is removed from a single role).
         //    The listener translates these into precise session invalidations for users
         //    assigned to each affected (role, department) pair.
         //
         //    Also fire DepartmentScopeChangedEvent(CHANGE_PERMISSIONS) per department where
         //    at least one role was modified, so any dept-scope caches downstream refresh.
         for (var code : rolesTouchedDeptCodes) {
            eventPublisher.publishDepartmentScopeChanged(new DepartmentScopeChangedEvent(
                    code, DepartmentScopeChangedEvent.CHANGE_PERMISSIONS, null));
         }
      }

      return ResponseEntity.noContent().build();
   }

   /**
    * Removes {@code permissionCode} from the {@link RoleEntity#getPermissions()} set of every
    * role in {@code roleIds}. For each role actually modified, publishes a
    * {@link RolePermissionChangedEvent} (mirrors {@code RemovePermissionsCommandHandler}) and
    * returns the set of department codes that had at least one role modified — used by the
    * caller to publish per-department scope-change events.
    */
   private Set<String> scrubPermissionFromRoles(Set<Integer> roleIds, String permissionCode) {
      if (roleIds.isEmpty()) return Set.of();
      Set<String> touchedDeptCodes = new HashSet<>();
      for (RoleEntity role : roleRepository.findAllById(roleIds)) {
         boolean modified = role.getPermissions().removeIf(
                 (PermissionEntity p) -> permissionCode.equals(p.getName()));
         if (modified) {
            roleRepository.save(role);
            String deptCode = role.getDepartment().getCode();
            touchedDeptCodes.add(deptCode);
            eventPublisher.publishRolePermissionChanged(new RolePermissionChangedEvent(
                    role.getCode(), deptCode, "PERMISSIONS_REMOVED", null));
            LOGGER.info("Cascade: removed permission '{}' from role '{}' in department '{}'",
                    permissionCode, role.getCode(), deptCode);
         }
      }
      return touchedDeptCodes;
   }

   /**
    * Depth-first walk of {@code dept} and its non-deleted descendants via
    * {@link DepartmentEntity#getChildrenids()}. The {@code acc} set is populated with the
    * dept itself and every non-deleted descendant reachable from it.
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

}
