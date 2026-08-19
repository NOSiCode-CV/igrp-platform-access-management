package cv.igrp.platform.access_management.app.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.app.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.role.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.department.application.commands.RemovePermissionsCommand;
import cv.igrp.platform.access_management.security_audit.domain.events.MenuDisassociatedFromRoleEvent;
import cv.igrp.platform.access_management.session.domain.event.RolePermissionChangedEvent;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.shared.domain.events.EventPublisher;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.service.ScopeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Command handler responsible for removing a list of roles from a specific menu entry.
 * <p>
 * This handler:
 * <ul>
 *     <li>Fetches the menu entry by its code, ensuring it is not marked as {@link Status#DELETED}</li>
 *     <li>Iterates over the list of role codes to remove</li>
 *     <li>Removes each matching role from the menu entry's role set (owning side of the join)</li>
 *     <li>Enforces the caller's department-management scope on every role's department</li>
 *     <li>Publishes per-role session-invalidation and audit events so users assigned to each
 *         removed role are logged out and re-authenticate without the revoked menu</li>
 * </ul>
 * The result is the updated {@link MenuEntryDTO}.
 *
 * @see RemoveRolesFromMenuCommand
 * @see MenuEntryEntity
 * @see PermissionEntity
 * @see MenuEntryEntityRepository
 * @see PermissionMapper
 * @see PermissionDTO
 * @see Status
 * @see IgrpResponseStatusException
 */
@Slf4j
@Component
public class RemoveRolesFromMenuCommandHandler implements CommandHandler<RemoveRolesFromMenuCommand, ResponseEntity<MenuEntryDTO>> {



   private final MenuEntryEntityRepository menuEntryRepository;
   private final ApplicationEntityRepository applicationRepository;
   private final MenuEntryMapper menuEntryMapper;
   private final EventPublisher eventPublisher;
   private final ScopeService scopeService;

   /**
    * Constructs a new instance of {@code RemoveRolesFromMenuCommandHandler} with the necessary dependencies.
    *
    * @param menuEntryRepository the repository used to retrieve and persist menu entry entities
    * @param applicationEntityRepository the repository used to retrieve applications by code
    * @param menuEntryMapper     the mapper used to convert between menu entry entities and DTO
    * @param eventPublisher      publishes session-invalidation and audit events for each removed role
    * @param scopeService        enforces the department-management scope on each role's department
    */
   public RemoveRolesFromMenuCommandHandler(MenuEntryEntityRepository menuEntryRepository,
                                            ApplicationEntityRepository applicationEntityRepository,
                                            MenuEntryMapper menuEntryMapper,
                                            EventPublisher eventPublisher,
                                            ScopeService scopeService) {
      this.menuEntryRepository = menuEntryRepository;
      this.applicationRepository = applicationEntityRepository;
      this.menuEntryMapper = menuEntryMapper;
      this.eventPublisher = eventPublisher;
      this.scopeService = scopeService;
   }

   /**
    * Handles the removal of roles from a menu entry.
    * <p>
    * For each role code provided in the {@link RemovePermissionsCommand}, the method checks whether
    * the role is currently associated with the menu entry. If so, the caller's scope over that role's
    * department is enforced, the role is removed from the menu entry, and per-role invalidation and
    * audit events are published so users holding the role lose the menu on their next token refresh.
    *
    * @param command the command containing the menu entry code and a list of role codes to remove
    * @return a {@link ResponseEntity} with the updated {@link MenuEntryDTO} and HTTP status {@code 200 OK}
    * @throws IgrpResponseStatusException if the menu entry does not exist or is marked as {@link Status#DELETED}
    */
   @IgrpCommandHandler
   @Transactional
   public ResponseEntity<MenuEntryDTO> handle(RemoveRolesFromMenuCommand command) {

      log.info("Remove Roles with name: {} from menu entry with code: {}.", command.getRemoveRolesFromMenuRequest().stream().toList(), command.getMenuCode());

      String appCode = command.getApplicationCode();

      var application = applicationRepository.findByCodeAndStatusNotDeleted(appCode);

      MenuEntryEntity foundMenu = menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(application, command.getMenuCode(), Status.DELETED)
              .orElseThrow(() -> {
                 log.warn("Menu Entry with code: {} not found.", command.getMenuCode());
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND, "Remove Role By Menu Entry code", "Menu Entry with code: " + command.getMenuCode() + " not found."
                 );
              });

      Set<String> targetRoleCodes = new HashSet<>(command.getRemoveRolesFromMenuRequest());
      List<RoleEntity> removed = new ArrayList<>();

      // Fix (P0 gap): previously this iterated with removeIf and never enforced scope nor
      // published any event. A caller from an unrelated department could strip roles from
      // any menu, and users holding those roles kept the menu in their JWT until the token
      // refreshed. Now we scope-check the role's department before removing, and fire the
      // per-role invalidation + audit events so sessions of assignees are invalidated.
      Iterator<RoleEntity> it = foundMenu.getRoles().iterator();
      while (it.hasNext()) {
         RoleEntity role = it.next();
         if (!targetRoleCodes.contains(role.getCode())) continue;
         var roleDept = role.getDepartment();
         if (roleDept == null) {
            // Defensive: a role without a department shouldn't exist in practice, but if
            // one shows up here, the scope check would NPE — skip it noisily.
            log.warn("Skipping role '{}' on menu '{}': role has no department (data inconsistency)",
                    role.getCode(), command.getMenuCode());
            continue;
         }
         scopeService.assertInScope(roleDept.getId());
         it.remove();
         removed.add(role);
      }

      var response = menuEntryMapper.toDTO(menuEntryRepository.save(foundMenu));

      for (RoleEntity role : removed) {
         String deptCode = role.getDepartment().getCode();
         eventPublisher.publishRolePermissionChanged(new RolePermissionChangedEvent(
                 role.getCode(), deptCode, "ROLE_MENUS_CHANGED", null));
         eventPublisher.publishSettingsAudit(
                 new MenuDisassociatedFromRoleEvent(command.getMenuCode(), deptCode));
      }

      log.info("Roles with code {} removed from menu with code: {} successfully.",
              removed.stream().map(RoleEntity::getCode).toList(), command.getMenuCode());
      return new ResponseEntity<>(response, HttpStatus.OK);

   }

}
