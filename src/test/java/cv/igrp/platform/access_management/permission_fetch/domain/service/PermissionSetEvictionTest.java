package cv.igrp.platform.access_management.permission_fetch.domain.service;

import cv.igrp.platform.access_management.active_role.domain.events.ActiveRoleChangedEvent;
import cv.igrp.platform.access_management.permission_fetch.infrastructure.cache.PermissionSetCache;
import cv.igrp.platform.access_management.session.domain.event.RolePermissionChangedEvent;
import cv.igrp.platform.access_management.shared.domain.events.DeletePermissionEvent;
import cv.igrp.platform.access_management.shared.domain.events.DepartmentScopeChangedEvent;
import cv.igrp.platform.access_management.shared.domain.events.UserRoleChangedEvent;
import cv.igrp.platform.access_management.shared.domain.events.UserStatusChangedEvent;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * v3-10 (#18): users are resolved where the event is published; their sets
 * are deleted after commit, once each. The end-to-end behaviour is in
 * {@code PermissionSetEvictionIT}; this covers the paths it cannot reach.
 */
@ExtendWith(MockitoExtension.class)
class PermissionSetEvictionTest {

    @Mock
    PermissionSetCache cache;
    @Mock
    IGRPUserEntityRepository users;
    @InjectMocks
    PermissionSetEviction eviction;

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.unbindResourceIfPossible(eviction);
    }

    @Test
    void outsideATransactionTheChangeIsCommittedSoItEvictsAtOnce() {
        when(users.findUserIdsByRoleAndDepartment("R1", "D1")).thenReturn(Set.of("u1", "u2"));

        eviction.onRolePermissionChanged(new RolePermissionChangedEvent("R1", "D1", "PERMISSIONS_ADDED", null));

        verify(cache).evictUser("u1");
        verify(cache).evictUser("u2");
    }

    @Test
    void insideATransactionItWaitsForTheCommitAndEvictsEachUserOnce() {
        TransactionSynchronizationManager.initSynchronization();
        when(users.findUserIdsByDepartment("D1")).thenReturn(Set.of("u1"));
        when(users.findUserIdsByPermissionName("svc.faturas.list")).thenReturn(Set.of("u1"));

        eviction.onUserRoleChanged(new UserRoleChangedEvent("u1", Set.of("R1"), "D1", "ADDED", "t"));
        eviction.onDepartmentScopeChanged(new DepartmentScopeChangedEvent("D1", "PERMISSIONS_CHANGED", "t"));
        eviction.onPermissionDeleted(new DeletePermissionEvent(this, "svc.faturas.list"));
        eviction.onUserStatusChanged(new UserStatusChangedEvent("u2", "ACTIVE", "INACTIVE", "t"));

        verifyNoInteractions(cache);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        TransactionSynchronization synchronization = TransactionSynchronizationManager.getSynchronizations().getFirst();

        synchronization.afterCommit();
        synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);

        verify(cache, times(1)).evictUser("u1");
        verify(cache, times(1)).evictUser("u2");
        assertThat(TransactionSynchronizationManager.hasResource(eviction)).isFalse();
    }

    @Test
    void aRollbackEvictsNothing() {
        TransactionSynchronizationManager.initSynchronization();
        eviction.onUserRoleChanged(new UserRoleChangedEvent("u1", Set.of("R1"), "D1", "ADDED", "t"));

        TransactionSynchronizationManager.getSynchronizations().getFirst()
                .afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(cache, never()).evictUser("u1");
        assertThat(TransactionSynchronizationManager.hasResource(eviction)).isFalse();
    }

    @Test
    void redisDownAfterCommitDoesNotFailTheCommittedChange() {
        doThrow(new RedisConnectionFailureException("down")).when(cache).evictUser("u1");
        doThrow(new RedisConnectionFailureException("down")).when(cache).evict("u1", "APP");

        assertThatCode(() -> {
            eviction.onUserStatusChanged(new UserStatusChangedEvent("u1", "ACTIVE", "INACTIVE", "t"));
            eviction.onActiveRoleChanged(new ActiveRoleChangedEvent("u1", "APP"));
        }).doesNotThrowAnyException();
        verify(cache).evictUser("u1");
        verify(cache).evict("u1", "APP");
    }

    @Test
    void anEventWithoutAUserEvictsNothing() {
        eviction.onUserStatusChanged(new UserStatusChangedEvent(null, "ACTIVE", "INACTIVE", "t"));

        verifyNoInteractions(cache, users);
    }

    @Test
    void aSuspendedTransactionKeepsItsOwnPendingUsers() {
        TransactionSynchronizationManager.initSynchronization();
        eviction.onUserRoleChanged(new UserRoleChangedEvent("outer", Set.of("R1"), "D1", "ADDED", "t"));
        TransactionSynchronization outer = TransactionSynchronizationManager.getSynchronizations().getFirst();

        // What REQUIRES_NEW does: suspend the outer synchronizations, start fresh ones.
        outer.suspend();
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.initSynchronization();
        eviction.onUserRoleChanged(new UserRoleChangedEvent("inner", Set.of("R1"), "D1", "ADDED", "t"));
        TransactionSynchronization inner = TransactionSynchronizationManager.getSynchronizations().getFirst();
        inner.afterCommit();
        inner.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        TransactionSynchronizationManager.clearSynchronization();
        outer.resume();
        outer.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(cache).evictUser("inner");
        verify(cache, never()).evictUser("outer");
    }

    @Test
    void aChangePublishedAfterOurCommitCallbackIsEvictedAtOnce() {
        TransactionSynchronizationManager.initSynchronization();
        eviction.onUserRoleChanged(new UserRoleChangedEvent("u1", Set.of("R1"), "D1", "ADDED", "t"));
        TransactionSynchronization synchronization = TransactionSynchronizationManager.getSynchronizations().getFirst();
        synchronization.afterCommit();

        eviction.onUserStatusChanged(new UserStatusChangedEvent("u2", "ACTIVE", "INACTIVE", "t"));

        verify(cache).evictUser("u2");
        synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
    }
}
