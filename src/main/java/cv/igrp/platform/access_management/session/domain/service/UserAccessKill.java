package cv.igrp.platform.access_management.session.domain.service;

import cv.igrp.platform.access_management.oauth_server.infrastructure.security.UserAuthorizations;
import cv.igrp.platform.access_management.shared.domain.events.UserAccessEndedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Kill (#19; spec "Logout, kill and refresh"): always the whole platform.
 * For each user, every session and every authorization is revoked, on every
 * device, and their permission keys are deleted after commit
 * ({@link UserAccessEndedEvent}), so the next request that misses the cache
 * gets 401. Every administrator kill path ends here.
 */
@Slf4j
@Service
public class UserAccessKill {

    private final SessionInvalidationService sessions;
    private final UserAuthorizations authorizations;
    private final ApplicationEventPublisher events;

    public UserAccessKill(SessionInvalidationService sessions, UserAuthorizations authorizations,
                          ApplicationEventPublisher events) {
        this.sessions = sessions;
        this.authorizations = authorizations;
        this.events = events;
    }

    /**
     * @param killedBy who asked, recorded on each revoked session ({@code SYSTEM} when null)
     * @return how many users were killed
     */
    @Transactional
    public int kill(Collection<String> userIds, String reason, String killedBy) {
        Set<String> users = new LinkedHashSet<>(userIds);
        users.remove(null);
        if (users.isEmpty()) {
            return 0;
        }
        sessions.invalidateUserSessions(users, reason, killedBy);
        int removed = users.stream().mapToInt(authorizations::removeAll).sum();
        events.publishEvent(new UserAccessEndedEvent(users, reason));
        log.info("Killed {} user(s) on the whole platform ({} authorization(s) removed), reason {}",
                users.size(), removed, reason);
        return users.size();
    }
}
