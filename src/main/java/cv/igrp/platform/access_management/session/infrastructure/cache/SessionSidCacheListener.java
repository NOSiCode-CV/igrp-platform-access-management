package cv.igrp.platform.access_management.session.infrastructure.cache;

import cv.igrp.platform.access_management.session.domain.constants.SessionStatus;
import cv.igrp.platform.access_management.session.infrastructure.persistence.entity.SessionEntity;
import jakarta.persistence.PostUpdate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Drops a session from the SID hot-path cache whenever its row stops being
 * ACTIVE, whichever code path closed, expired or revoked it. The enforcement
 * filter trusts that cache, so a missed eviction would keep a dead session
 * working until the entry expires.
 *
 * <p>Bulk JPQL updates bypass entity listeners; they evict through
 * {@link SessionCacheEvictService#evictBySubjects}.
 */
@Component
public class SessionSidCacheListener {

    // A provider, because the cache service needs repositories, which need the
    // EntityManagerFactory that is creating this listener.
    private final ObjectProvider<SessionCacheEvictService> evictService;

    public SessionSidCacheListener(ObjectProvider<SessionCacheEvictService> evictService) {
        this.evictService = evictService;
    }

    @PostUpdate
    public void onUpdate(SessionEntity session) {
        if (session.getStatus() != SessionStatus.ACTIVE && session.getSessionId() != null) {
            evictService.getObject().evictSid(session.getSessionId());
        }
    }
}
