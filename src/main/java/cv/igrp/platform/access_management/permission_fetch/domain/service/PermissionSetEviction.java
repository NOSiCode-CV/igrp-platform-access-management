package cv.igrp.platform.access_management.permission_fetch.domain.service;

import cv.igrp.platform.access_management.active_role.domain.events.ActiveRoleChangedEvent;
import cv.igrp.platform.access_management.permission_fetch.infrastructure.cache.PermissionSetCache;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Deletes a user's permission set when their active role in the application
 * changes (#13), only after the switch commits: a concurrent request then
 * cannot reload the old role into Redis. The other evictions (role and
 * permission changes, logout) come with #18.
 */
@Component
public class PermissionSetEviction {

    private final PermissionSetCache cache;

    public PermissionSetEviction(PermissionSetCache cache) {
        this.cache = cache;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onActiveRoleChanged(ActiveRoleChangedEvent event) {
        // A user's sub is their user id (SubjectParser).
        cache.evict(event.userId(), event.applicationCode());
    }
}
