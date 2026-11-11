package cv.igrp.platform.access_management.permission_fetch.domain.service;

import cv.igrp.platform.access_management.active_role.domain.events.ActiveRoleChangedEvent;
import cv.igrp.platform.access_management.permission_fetch.infrastructure.cache.PermissionSetCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Deletes a user's permission set when their active role in the application
 * changes (#13), after the switch commits, so the next request reads the new
 * role. A fetch already in flight with the old role can still write it back;
 * the spec accepts that race, bounded by the TTL. The other evictions (role
 * and permission changes, logout) come with #18.
 */
@Component
public class PermissionSetEviction {

    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionSetEviction.class);

    private final PermissionSetCache cache;

    public PermissionSetEviction(PermissionSetCache cache) {
        this.cache = cache;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onActiveRoleChanged(ActiveRoleChangedEvent event) {
        try {
            // A user's sub is their user id (SubjectParser).
            cache.evict(event.userId(), event.applicationCode());
        } catch (DataAccessException e) {
            // The switch is committed; the stale set expires with its TTL.
            LOGGER.warn("[PERMISSIONS] Could not evict the permission set of user {} in {}: {}",
                    event.userId(), event.applicationCode(), e.getMessage());
        }
    }
}
