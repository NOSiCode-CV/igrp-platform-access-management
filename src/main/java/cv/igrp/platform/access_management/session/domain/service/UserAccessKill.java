package cv.igrp.platform.access_management.session.domain.service;

import cv.igrp.platform.access_management.oauth_server.infrastructure.security.UserAuthorizations;
import cv.igrp.platform.access_management.security_audit.application.service.AccessAuditLogger;
import cv.igrp.platform.access_management.shared.domain.events.UserAccessEndedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Kill (#19, #33; spec "Logout, kill and refresh"): always the whole platform. For each user,
 * every authorization is revoked, on every device, and their permission keys are deleted after
 * commit ({@link UserAccessEndedEvent}), so the next request that misses the cache gets 401.
 * Every administrator kill path ends here.
 */
@Slf4j
@Service
public class UserAccessKill {

    private final UserAuthorizations authorizations;
    private final ApplicationEventPublisher events;
    private final AccessAuditLogger audit;

    public UserAccessKill(UserAuthorizations authorizations, ApplicationEventPublisher events,
                          AccessAuditLogger audit) {
        this.authorizations = authorizations;
        this.events = events;
        this.audit = audit;
    }

    /**
     * @param killedBy who asked, recorded in the audit trail ({@code SYSTEM} when null)
     * @return how many users were killed
     */
    @Transactional
    public int kill(Collection<String> userIds, String reason, String killedBy) {
        Set<String> users = new LinkedHashSet<>(userIds);
        users.remove(null);
        if (users.isEmpty()) {
            return 0;
        }
        int removed = users.stream().mapToInt(authorizations::removeAll).sum();
        users.forEach(user -> audit.recordAccessEnded(user, reason, killedBy));
        events.publishEvent(new UserAccessEndedEvent(users, reason));
        log.info("Killed {} user(s) on the whole platform ({} authorization(s) removed), reason {}, by {}",
                users.size(), removed, reason, killedBy);
        return users.size();
    }
}
