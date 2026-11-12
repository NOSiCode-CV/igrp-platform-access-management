package cv.igrp.platform.access_management.permission_fetch.domain.service;

import cv.igrp.platform.access_management.active_role.domain.events.ActiveRoleChangedEvent;
import cv.igrp.platform.access_management.permission_fetch.infrastructure.cache.PermissionSetCache;
import cv.igrp.platform.access_management.session.domain.event.RolePermissionChangedEvent;
import cv.igrp.platform.access_management.shared.domain.events.DeletePermissionEvent;
import cv.igrp.platform.access_management.shared.domain.events.DepartmentScopeChangedEvent;
import cv.igrp.platform.access_management.shared.domain.events.UserAccessEndedEvent;
import cv.igrp.platform.access_management.shared.domain.events.UserRoleChangedEvent;
import cv.igrp.platform.access_management.shared.domain.events.UserStatusChangedEvent;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
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
 * back; the spec accepts that race, bounded by the TTL.
 */
@Component
public class PermissionSetEviction {

    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionSetEviction.class);

    /** Service accounts holding a role, by code, optionally in one department; their sub is their id. */
    private static final String SERVICE_ACCOUNTS_WITH_ROLE = """
            SELECT DISTINCT sara.service_account_id::text FROM t_service_account_role_assignment sara
              JOIN t_role r ON r.id = sara.role_id
              JOIN t_department d ON d.id = r.department
             WHERE r.code = ? AND (CAST(? AS varchar) IS NULL OR d.code = ?)""";

    /** Service accounts holding a role in a department; their sub is their id. */
    private static final String SERVICE_ACCOUNTS_IN_DEPARTMENT = """
            SELECT DISTINCT sara.service_account_id::text FROM t_service_account_role_assignment sara
              JOIN t_role r ON r.id = sara.role_id
              JOIN t_department d ON d.id = r.department
             WHERE d.code = ?""";

    private final PermissionSetCache cache;
    private final IGRPUserEntityRepository users;
    private final JdbcTemplate jdbc;

    public PermissionSetEviction(PermissionSetCache cache, IGRPUserEntityRepository users, JdbcTemplate jdbc) {
        this.cache = cache;
        this.users = users;
        this.jdbc = jdbc;
    }

    /** Roles assigned, removed or expired, and the global active role switched. */
    @EventListener
    public void onUserRoleChanged(UserRoleChangedEvent event) {
        evictAfterCommit(user(event.getUserId()));
    }

    /** A logout or a kill (#19): every key of the user, so the next miss gets 401. */
    @EventListener
    public void onUserAccessEnded(UserAccessEndedEvent event) {
        evictAfterCommit(event.userIds());
    }

    /** A user disabled, deleted or otherwise changing status. */
    @EventListener
    public void onUserStatusChanged(UserStatusChangedEvent event) {
        evictAfterCommit(user(event.getUserId()));
    }

    /**
     * Permissions added to or removed from a role, their scopes changed (#21),
     * or the role deleted: the role's users and the service accounts holding it.
     */
    @EventListener
    public void onRolePermissionChanged(RolePermissionChangedEvent event) {
        evictAfterCommit(users.findUserIdsByRoleAndDepartment(event.getRoleCode(), event.getDepartmentCode()));
        evictAfterCommit(jdbc.queryForList(SERVICE_ACCOUNTS_WITH_ROLE, String.class,
                event.getRoleCode(), event.getDepartmentCode(), event.getDepartmentCode()));
    }

    /** A department's permissions, applications, menus, resources or status changed. */
    @EventListener
    public void onDepartmentScopeChanged(DepartmentScopeChangedEvent event) {
        evictAfterCommit(users.findUserIdsByDepartment(event.getDepartmentCode()));
        // Service accounts holding a role there: their visible departments too (#39).
        evictAfterCommit(jdbc.queryForList(SERVICE_ACCOUNTS_IN_DEPARTMENT, String.class, event.getDepartmentCode()));
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

    /** Deletes these subjects' sets after the current transaction commits (at once without one). */
    public void evictUsersAfterCommit(Collection<String> userIds) {
        evictAfterCommit(userIds);
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
