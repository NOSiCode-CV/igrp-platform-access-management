package cv.igrp.platform.access_management.shared.infrastructure.cache;

import cv.igrp.platform.access_management.shared.domain.events.DeletePermissionEvent;
import cv.igrp.platform.access_management.shared.domain.events.ResourcePermissionDeletedEvent;
import cv.igrp.platform.access_management.shared.domain.events.UserRoleChangedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.mockito.Mockito.verify;

/**
 * The second link in the chain that makes an expired role stop granting access.
 *
 * <p>Permission decisions are memoised by
 * {@code PermissionCacheService.getOrLoadPermissionCached} in a Redis cache with
 * <b>no TTL</b>. Every SQL path already filters
 * {@code expires_at > NOW()}, so the database answer is correct the instant an
 * assignment expires — but a decision cached as "granted" beforehand is never
 * re-derived. {@link UserRoleChangedEvent} is the only thing that evicts it.
 *
 * <p>So the chain is: {@code ExpireRoleService} publishes the event (asserted in
 * {@code ExpireRoleServiceTest}) and this listener turns it into an eviction
 * (asserted here). Both links are needed; neither is sufficient.
 *
 * <p>This is deliberately <b>not</b> an end-to-end proof. That would need a
 * Spring context plus a real Redis to cache a decision, expire the role and
 * re-check — worth having, and absent because the suite starts no application
 * context at all.
 */
@ExtendWith(MockitoExtension.class)
class PermissionCacheInvalidatorTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000042";

    @Mock
    private PermissionCacheEvictService evictService;

    @InjectMocks
    private PermissionCacheInvalidator invalidator;

    @Test
    @DisplayName("An expired role evicts that user's cached permission decisions")
    void expiredRoleEvictsBySubject() {
        UserRoleChangedEvent event = new UserRoleChangedEvent(
                USER_ID,
                Set.of("TEMP_ROLE"),
                null,
                UserRoleChangedEvent.CHANGE_EXPIRED,
                "SYSTEM");

        invalidator.onUserRoleChanged(event);

        verify(evictService).evictBySubject(USER_ID);
    }

    @Test
    @DisplayName("Eviction does not depend on the change type")
    void everyChangeTypeEvicts() {
        // The listener must not start branching on changeType. CHANGE_EXPIRED was
        // added purely so logs can distinguish a revocation from the clock
        // running out; if a future change made eviction conditional on the type,
        // expiry would silently stop evicting again.
        for (String changeType : new String[]{
                UserRoleChangedEvent.CHANGE_ADDED,
                UserRoleChangedEvent.CHANGE_REMOVED,
                UserRoleChangedEvent.CHANGE_ACTIVE_ROLE_CHANGED,
                UserRoleChangedEvent.CHANGE_EXPIRED}) {

            PermissionCacheEvictService perTypeEvictService =
                    org.mockito.Mockito.mock(PermissionCacheEvictService.class);
            PermissionCacheInvalidator perTypeInvalidator =
                    new PermissionCacheInvalidator(perTypeEvictService);

            perTypeInvalidator.onUserRoleChanged(new UserRoleChangedEvent(
                    USER_ID, Set.of("ANY_ROLE"), null, changeType, "SYSTEM"));

            verify(perTypeEvictService).evictBySubject(USER_ID);
        }
    }

    @Test
    @DisplayName("A deleted permission evicts by action")
    void deletedPermissionEvictsByAction() {
        invalidator.onPermissionDeleted(new DeletePermissionEvent(this, "igrp.users.create"));

        verify(evictService).evictByAction("igrp.users.create");
    }

    @Test
    @DisplayName("An unlinked resource evicts by resource")
    void unlinkedResourceEvictsByResource() {
        invalidator.onResourceUnlinked(
                new ResourcePermissionDeletedEvent(this, "igrp-access-management"));

        verify(evictService).evictByResource("igrp-access-management");
    }
}
