package cv.igrp.platform.access_management.session.application.listener;

import cv.igrp.platform.access_management.session.domain.event.RolePermissionChangedEvent;
import cv.igrp.platform.access_management.session.domain.service.SessionInvalidationService;
import cv.igrp.platform.access_management.session.infrastructure.audit.SessionAuditLogger;
import cv.igrp.platform.access_management.shared.domain.events.DeletePermissionEvent;
import cv.igrp.platform.access_management.shared.domain.events.DepartmentScopeChangedEvent;
import cv.igrp.platform.access_management.shared.domain.events.UserRoleChangedEvent;
import cv.igrp.platform.access_management.shared.domain.events.UserStatusChangedEvent;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Event listener for role/permission/user/department changes that require
 * server-side session invalidation. Receives events fired by the publishers
 * wired in Phase D — see {@code EventPublisher}.
 */
@Slf4j
@Component
public class SessionInvalidationEventListener {

    private final SessionInvalidationService sessionInvalidationService;
    private final IGRPUserEntityRepository userRepository;
    private final SessionAuditLogger sessionAuditLogger;

    public SessionInvalidationEventListener(SessionInvalidationService sessionInvalidationService,
                                            IGRPUserEntityRepository userRepository,
                                            SessionAuditLogger sessionAuditLogger) {
        this.sessionInvalidationService = sessionInvalidationService;
        this.userRepository = userRepository;
        this.sessionAuditLogger = sessionAuditLogger;
    }

    /**
     * Handle user role changes — invalidate sessions for the specific user.
     */
    @EventListener
    public void handleUserRoleChanged(UserRoleChangedEvent event) {
        String userId = event.getUserId();
        if (userId == null) {
            log.warn("UserRoleChangedEvent received without userId; skipping invalidation");
            return;
        }
        log.info("User role changed for userId={} ({}), invalidating sessions",
                userId, event.getChangeType());
        sessionInvalidationService.invalidateUserSession(userId, "USER_ROLE_CHANGED");
        sessionAuditLogger.recordRevoked(null, userId, "USER_ROLE_CHANGED", SessionAuditLogger.SYSTEM);
    }

    /**
     * Handle role permission changes — invalidate sessions for all users with that role.
     */
    @EventListener
    public void handleRolePermissionChanged(RolePermissionChangedEvent event) {
        String departmentCode = event.getDepartmentCode();
        String roleCode = event.getRoleCode();
        log.info("Role permissions changed for role={} dept={} ({}), invalidating sessions",
                roleCode, departmentCode, event.getChangeType());
        sessionInvalidationService.invalidateSessionsByRole(departmentCode, roleCode, "ROLE_PERMISSIONS_CHANGED");
        sessionAuditLogger.recordRevoked(null, null, "ROLE_PERMISSIONS_CHANGED", SessionAuditLogger.SYSTEM);
    }

    /**
     * Handle user status changes — kill every active session for the user.
     */
    @EventListener
    public void handleUserStatusChanged(UserStatusChangedEvent event) {
        String userId = event.getUserId();
        if (userId == null) {
            log.warn("UserStatusChangedEvent received without userId; skipping invalidation");
            return;
        }
        log.info("User status changed for userId={} ({} -> {}), invalidating sessions",
                userId, event.getPreviousStatus(), event.getNewStatus());
        sessionInvalidationService.invalidateUserSession(userId, "USER_STATUS_CHANGED");
        sessionAuditLogger.recordRevoked(null, userId, "USER_STATUS_CHANGED", SessionAuditLogger.SYSTEM);
    }

    /**
     * Handle department scope changes — invalidate sessions for every user in the department.
     */
    @EventListener
    public void handleDepartmentScopeChanged(DepartmentScopeChangedEvent event) {
        String departmentCode = event.getDepartmentCode();
        if (departmentCode == null || departmentCode.isBlank()) {
            log.warn("DepartmentScopeChangedEvent received without departmentCode; skipping invalidation");
            return;
        }
        log.info("Department scope changed for dept={} ({}), invalidating sessions",
                departmentCode, event.getChangeType());
        sessionInvalidationService.invalidateSessionsByDepartment(departmentCode, "DEPARTMENT_SCOPE_CHANGED");
        sessionAuditLogger.recordRevoked(null, null, "DEPARTMENT_SCOPE_CHANGED", SessionAuditLogger.SYSTEM);
    }

    /**
     * Handle permission deletions — resolve every user that held the permission
     * and invalidate their sessions (Phase D8).
     */
    @EventListener
    public void handlePermissionDeleted(DeletePermissionEvent event) {
        String permissionName = event.permissionName();
        if (permissionName == null || permissionName.isBlank()) {
            log.warn("DeletePermissionEvent received without permissionName; skipping invalidation");
            return;
        }

        Set<String> affectedUsers = userRepository.findUserIdsByPermissionName(permissionName);
        if (affectedUsers.isEmpty()) {
            log.info("Permission deleted: {} — no active users held it", permissionName);
            return;
        }
        log.info("Permission deleted: {} — invalidating sessions for {} affected user(s)",
                permissionName, affectedUsers.size());
        sessionInvalidationService.invalidateUserSessions(affectedUsers, "PERMISSION_DELETED");
        affectedUsers.forEach(uid ->
                sessionAuditLogger.recordRevoked(null, uid, "PERMISSION_DELETED", SessionAuditLogger.SYSTEM));
    }
}
