package cv.igrp.platform.access_management.permission_fetch.domain.service;

import cv.igrp.platform.access_management.active_role.domain.events.ActiveRoleChangedEvent;
import cv.igrp.platform.access_management.permission_fetch.infrastructure.cache.PermissionSetCache;
import cv.igrp.platform.access_management.session.domain.event.RolePermissionChangedEvent;
import cv.igrp.platform.access_management.shared.domain.events.DeletePermissionEvent;
import cv.igrp.platform.access_management.shared.domain.events.DepartmentScopeChangedEvent;
import cv.igrp.platform.access_management.shared.domain.events.UserRoleChangedEvent;
import cv.igrp.platform.access_management.shared.domain.events.UserStatusChangedEvent;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Deletes permission sets when a user's effective permissions change (v3-10,
 * #18; spec "Cache"), so the change takes effect at their next request.
 * <p>
 * Each listener runs where the event is published, inside the changing
 * transaction, and only works out which users are affected: a role or a
 * department is still linked to its users at that point, even when the change
 * deletes it. Their sets, found through the per-user index, are deleted once,
 * after the transaction commits; a rollback deletes nothing. A change made
 * outside a transaction is already committed, so it is evicted at once.
 * <p>
 * A fetch already in flight with the old permissions can still write them
 * back; the spec accepts that race, bounded by the TTL. Logout and kill come
 * with #19.
 */
@Component
public class PermissionSetEviction {

    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionSetEviction.class);

    private final PermissionSetCache cache;
    private final IGRPUserEntityRepository users;

    public PermissionSetEviction(PermissionSetCache cache, IGRPUserEntityRepository users) {
        this.cache = cache;
        this.users = users;
    }

    /** Roles assigned, removed or expired, and the global active role switched. */
    @EventListener
    public void onUserRoleChanged(UserRoleChangedEvent event) {
        evictAfterCommit(user(event.getUserId()));
    }

    /** A user disabled, deleted or otherwise changing status. */
    @EventListener
    public void onUserStatusChanged(UserStatusChangedEvent event) {
        evictAfterCommit(user(event.getUserId()));
    }

    /** Permissions added to or removed from a role, or the role deleted. */
    @EventListener
    public void onRolePermissionChanged(RolePermissionChangedEvent event) {
        evictAfterCommit(users.findUserIdsByRoleAndDepartment(event.getRoleCode(), event.getDepartmentCode()));
    }

    /** A department's permissions, applications, menus, resources or status changed. */
    @EventListener
    public void onDepartmentScopeChanged(DepartmentScopeChangedEvent event) {
        evictAfterCommit(users.findUserIdsByDepartment(event.getDepartmentCode()));
    }

    /** A permission deleted: every user who held it through a role. */
    @EventListener
    public void onPermissionDeleted(DeletePermissionEvent event) {
        evictAfterCommit(users.findUserIdsByPermissionName(event.permissionName()));
    }

    /**
     * The active role in one application switched (#13): only that
     * application's set. Without a transaction the switch is already
     * committed, so it is evicted at once.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onActiveRoleChanged(ActiveRoleChangedEvent event) {
        try {
            // A user's sub is their user id (SubjectParser).
            cache.evict(event.userId(), event.applicationCode());
        } catch (RuntimeException e) {
            // The switch is committed; the stale set expires with its TTL.
            LOGGER.warn("[PERMISSIONS] Could not evict the permission set of user {} in {}: {}",
                    event.userId(), event.applicationCode(), e.getMessage());
        }
    }

    private static Set<String> user(String userId) {
        return userId == null ? Set.of() : Set.of(userId);
    }

    private void evictAfterCommit(Collection<String> userIds) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evictNow(userIds);
            return;
        }
        Pending pending = (Pending) TransactionSynchronizationManager.getResource(this);
        if (pending != null && pending.evicted) {
            // Published from another after-commit callback once ours has run:
            // that change is committed too.
            evictNow(userIds);
            return;
        }
        if (pending == null) {
            pending = new Pending();
            TransactionSynchronizationManager.bindResource(this, pending);
            TransactionSynchronizationManager.registerSynchronization(pending);
        }
        pending.userIds.addAll(userIds);
    }

    private void evictNow(Collection<String> userIds) {
        userIds.forEach(userId -> {
            try {
                // A user's sub is their user id (SubjectParser).
                cache.evictUser(userId);
            } catch (RuntimeException e) {
                // The change is committed; the stale sets expire with their TTL.
                LOGGER.warn("[PERMISSIONS] Could not evict the permission sets of user {}: {}",
                        userId, e.getMessage());
            }
        });
    }

    /**
     * The users to evict when one transaction commits, each once however many
     * events name them. Bound to the transaction as a resource; a suspended
     * transaction (REQUIRES_NEW, NOT_SUPPORTED) unbinds it, so the inner one
     * gets its own and each evicts on its own commit.
     */
    private final class Pending implements TransactionSynchronization {

        private final Set<String> userIds = new LinkedHashSet<>();
        private boolean evicted;

        @Override
        public void suspend() {
            TransactionSynchronizationManager.unbindResource(PermissionSetEviction.this);
        }

        @Override
        public void resume() {
            TransactionSynchronizationManager.bindResource(PermissionSetEviction.this, this);
        }

        @Override
        public void afterCommit() {
            evicted = true;
            evictNow(userIds);
        }

        @Override
        public void afterCompletion(int status) {
            TransactionSynchronizationManager.unbindResourceIfPossible(PermissionSetEviction.this);
        }
    }
}
