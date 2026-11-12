package cv.igrp.platform.access_management.routes.domain.service;

import cv.igrp.platform.access_management.permission_fetch.domain.service.PermissionSetEviction;
import cv.igrp.platform.access_management.session.domain.event.RolePermissionChangedEvent;
import cv.igrp.platform.access_management.shared.domain.events.EventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Whoever holds a route key, so their cached permission sets are evicted after
 * commit when the key is deleted or renamed (#11): the roles it is assigned to
 * (their users and service accounts, through {@link RolePermissionChangedEvent})
 * and the service accounts it is granted to directly. Called before the key's
 * assignments change, while they still point at the holders.
 */
@Component
public class PermissionHolders {

    private static final String ROLES = """
            SELECT r.code AS role_code, d.code AS department_code FROM t_role_permission rp
              JOIN t_role r ON r.id = rp.role_id
              LEFT JOIN t_department d ON d.id = r.department
             WHERE rp.permission = ?""";

    private static final String GRANTED_SERVICE_ACCOUNTS =
            "SELECT service_account_id::text FROM t_service_account_permission_grant WHERE permission_id = ?";

    private final JdbcTemplate jdbc;
    private final EventPublisher events;
    private final PermissionSetEviction eviction;

    public PermissionHolders(JdbcTemplate jdbc, EventPublisher events, PermissionSetEviction eviction) {
        this.jdbc = jdbc;
        this.events = events;
        this.eviction = eviction;
    }

    /** Evicts, after commit, the cached sets of everyone holding the key. */
    public void evictAfterCommit(int permissionId, String changeType) {
        List<Map<String, Object>> roles = Objects.requireNonNullElse(jdbc.queryForList(ROLES, permissionId), List.of());
        for (Map<String, Object> role : roles) {
            events.publishRolePermissionChanged(new RolePermissionChangedEvent(
                    (String) role.get("role_code"), (String) role.get("department_code"), changeType, null));
        }
        eviction.evictUsersAfterCommit(Objects.requireNonNullElse(
                jdbc.queryForList(GRANTED_SERVICE_ACCOUNTS, String.class, permissionId), List.of()));
    }
}
