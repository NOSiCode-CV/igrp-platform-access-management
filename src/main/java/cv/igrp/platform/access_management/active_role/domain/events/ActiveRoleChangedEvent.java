package cv.igrp.platform.access_management.active_role.domain.events;

/**
 * A user's active role in an application changed (v3-05). Published in the
 * switch's transaction; the permission-key eviction (#14) listens after commit,
 * so the next request of that user for that application reads the new role.
 */
public record ActiveRoleChangedEvent(String userId, String applicationCode) {
}
