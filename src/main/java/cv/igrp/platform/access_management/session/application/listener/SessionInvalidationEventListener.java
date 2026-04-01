package cv.igrp.platform.access_management.session.application.listener;

import cv.igrp.platform.access_management.session.domain.service.SessionInvalidationService;
import cv.igrp.platform.access_management.shared.domain.events.DeletePermissionEvent;
import cv.igrp.platform.access_management.shared.domain.events.UserRoleChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Event listener for role/permission changes that require session invalidation
 */
@Slf4j
@Component
public class SessionInvalidationEventListener {

    private final SessionInvalidationService sessionInvalidationService;

    public SessionInvalidationEventListener(SessionInvalidationService sessionInvalidationService) {
        this.sessionInvalidationService = sessionInvalidationService;
    }

    /**
     * Handle user role changes - invalidate sessions for the specific user
     */
    @EventListener
    public void handleUserRoleChanged(UserRoleChangedEvent event) {
        String userExternalId = event.username();
        log.info("User role changed for: {}, invalidating sessions", userExternalId);
        
        sessionInvalidationService.invalidateUserSession(userExternalId, "USER_ROLE_CHANGED");
    }

    /**
     * Handle permission deletions - this would need to be extended to handle
     * permission additions/removals from roles
     */
    @EventListener
    public void handlePermissionDeleted(DeletePermissionEvent event) {
        String permissionName = event.permissionName();
        log.info("Permission deleted: {}, invalidating affected sessions", permissionName);
        
        // This is a simplified approach - in practice, you'd need to find
        // all users who had this permission and invalidate their sessions
        // For now, we'll log this as it would require additional queries
        log.warn("Permission deletion invalidation not fully implemented - would need to find users with permission: {}", permissionName);
    }

    /**
     * Handle role permission changes - this would need additional events
     * to be published when role permissions are modified
     */
    // @EventListener
    // public void handleRolePermissionChanged(RolePermissionChangedEvent event) {
    //     String departmentCode = event.getDepartmentCode();
    //     String roleCode = event.getRoleCode();
    //     log.info("Role permissions changed for: {} in department: {}, invalidating sessions", 
    //             roleCode, departmentCode);
    //     
    //     sessionInvalidationService.invalidateSessionsByRole(departmentCode, roleCode, "ROLE_PERMISSIONS_CHANGED");
    // }
}
