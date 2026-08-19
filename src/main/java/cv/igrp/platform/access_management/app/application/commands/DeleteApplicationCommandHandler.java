package cv.igrp.platform.access_management.app.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpErrorCode;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.events.DepartmentScopeChangedEvent;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import cv.igrp.platform.access_management.shared.domain.events.EventPublisher;
import cv.igrp.platform.access_management.security_audit.domain.events.ApplicationDeletedEvent;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * Command handler responsible for logically deleting an {@link ApplicationEntity}.
 *
 * <p>
 * This handler receives a {@link DeleteApplicationCommand}, verifies the existence of the application
 * by its ID, and performs a soft delete by setting its {@link Status} to {@link Status#DELETED}.
 * </p>
 *
 * <p>
 * As part of the delete, this handler ALSO scrubs the application from every role that
 * granted it (owning side {@code t_application_role}) and every department that exposed it
 * (owning side {@code t_department_application}), then publishes
 * {@link DepartmentScopeChangedEvent}({@link DepartmentScopeChangedEvent#CHANGE_APPLICATIONS})
 * per affected department so users' JWT {@code resource_access} claims are refreshed on
 * their next authenticated request.
 * </p>
 *
 * <p>
 * If the application is not found, an {@link IgrpResponseStatusException} is thrown with HTTP status {@code 404 NOT_FOUND}.
 * </p>
 *
 * @see DeleteApplicationCommand
 * @see ApplicationEntity
 * @see ApplicationEntityRepository
 * @see Status
 * @see IgrpResponseStatusException
 */
@Component
public class DeleteApplicationCommandHandler implements CommandHandler<DeleteApplicationCommand, ResponseEntity<String>> {

   private final ApplicationEntityRepository applicationRepository;
   private final MenuEntryEntityRepository menuRepository;
   private final DepartmentEntityRepository departmentRepository;
   private final EventPublisher eventPublisher;


   /**
    * Constructs the handler with the required repository dependency.
    *
    * @param applicationRepository the repository used to fetch and persist {@link ApplicationEntity} entities
    * @param menuRepository the repository used to fetch and persist {@link MenuEntryEntity} entities
    * @param departmentRepository repository used to persist department-side edits when the app link is cleared
    * @param eventPublisher publishes the settings-audit event and per-department scope-change events
    */
   public DeleteApplicationCommandHandler(ApplicationEntityRepository applicationRepository,
                                          MenuEntryEntityRepository menuRepository,
                                          DepartmentEntityRepository departmentRepository,
                                          EventPublisher eventPublisher) {
      this.applicationRepository = applicationRepository;
      this.menuRepository = menuRepository;
      this.departmentRepository = departmentRepository;
      this.eventPublisher = eventPublisher;
   }

   /**
    * Handles the soft deletion of an application by ID.
    *
    * <ul>
    *   <li>Fetches the application from the repository.</li>
    *   <li>If found, cascades the delete by clearing the application from every role and
    *   department that referenced it, sets its status to {@link Status#DELETED}, and saves.</li>
    *   <li>Fires session-invalidation events per affected department.</li>
    *   <li>If not found, throws {@link IgrpResponseStatusException}.</li>
    * </ul>
    *
    * @param command the command containing the ID of the application to delete
    * @return a {@link ResponseEntity} with HTTP {@code 204 NO_CONTENT} on success
    */
   @IgrpCommandHandler
   @Transactional
   public ResponseEntity<String> handle(DeleteApplicationCommand command) {
      ApplicationEntity application = applicationRepository.findByCodeAndStatusNot(command.getCode(), Status.DELETED)
              .orElseThrow(() -> IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_APPLICATION_NOT_FOUND_BY_CODE, command.getCode()));

      deleteApplicationMenus(application);

      // Fix (P1 gap): the app used to be marked DELETED without touching the role-side or
      // department-side joins. Roles kept granting a deleted app via t_application_role,
      // and dept.getApplications() still listed it — so JWT resource_access claims for
      // holders of those roles kept the deleted app until token refresh. Clear both sides
      // and fire per-dept scope-change events so sessions are invalidated.
      Set<String> touchedDeptCodes = new HashSet<>();
      if (application.getDepartments() != null) {
         // Copy to avoid ConcurrentModificationException while iterating and mutating both sides.
         List<DepartmentEntity> depts = new ArrayList<>(application.getDepartments());
         for (DepartmentEntity dept : depts) {
            if (dept.getApplications() != null && dept.getApplications().remove(application)) {
               touchedDeptCodes.add(dept.getCode());
               departmentRepository.save(dept);
            }
         }
      }
      if (application.getRoles() != null) {
         application.getRoles().clear();
      }

      application.setStatus(Status.DELETED);
      applicationRepository.save(application);

      eventPublisher.publishSettingsAudit(new ApplicationDeletedEvent(application.getName()));

      for (String deptCode : touchedDeptCodes) {
         eventPublisher.publishDepartmentScopeChanged(new DepartmentScopeChangedEvent(
                 deptCode, DepartmentScopeChangedEvent.CHANGE_APPLICATIONS, null));
      }

      return ResponseEntity.noContent().build();
   }

   private void deleteApplicationMenus(ApplicationEntity application) {

      if(!application.getMenus().isEmpty()) {

         for (var menu : application.getMenus()) {

            if(menu.getStatus().equals(Status.DELETED)) continue;

            var menuEntry = menuRepository.findByApplicationIdAndCodeAndStatusNot(application, menu.getCode(), Status.DELETED).orElseThrow(() -> IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_MENU_ENTRY_NOT_FOUND, menu.getCode()));

            menuEntry.setStatus(Status.DELETED);

            menuRepository.save(menuEntry);

         }

      }

   }

}
