package cv.igrp.platform.access_management.shared.domain.events;

import java.util.Set;

/**
 * A user's access ended on the whole platform (#19): a logout or an
 * administrator's kill. Their permission keys are deleted after commit, so
 * the next request that misses the cache gets 401.
 *
 * @param userIds the users whose access ended
 * @param reason  why, for logs: {@code USER_LOGOUT}, an admin reason
 */
public record UserAccessEndedEvent(Set<String> userIds, String reason) {

    public UserAccessEndedEvent {
        userIds = Set.copyOf(userIds);
    }
}
