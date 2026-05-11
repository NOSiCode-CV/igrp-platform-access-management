package cv.igrp.platform.access_management.shared.infrastructure.cache;

import cv.igrp.platform.access_management.shared.domain.events.DeletePermissionEvent;
import cv.igrp.platform.access_management.shared.domain.events.ResourcePermissionDeletedEvent;
import cv.igrp.platform.access_management.shared.domain.events.UserRoleChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PermissionCacheInvalidator {

    private final PermissionCacheEvictService evictService;

    @EventListener
    public void onPermissionDeleted(DeletePermissionEvent event) {
        evictService.evictByAction(event.permissionName());
    }

    @EventListener
    public void onUserRoleChanged(UserRoleChangedEvent event) {
        evictService.evictBySubject(String.valueOf(event.getUserId()));
    }

    @EventListener
    public void onResourceUnlinked(ResourcePermissionDeletedEvent event) {
        evictService.evictByResource(event.resourceName());
    }
}
