package cv.igrp.platform.access_management.routes.domain.service;

import cv.igrp.platform.access_management.security_audit.domain.events.PermissionDeletedEvent;
import cv.igrp.platform.access_management.session.domain.event.RolePermissionChangedEvent;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.events.EventPublisher;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deletes the route keys whose grace period is over (v3-03, #11; spec "Route
 * registration contract"). A registration from a service's newest version
 * schedules the keys it no longer declares; a later registration that declares
 * one again, of any version, cancels it. When due, a key is unassigned from
 * every role, department, service account, resource and menu item, marked
 * DELETED, audited, and everyone who held it has their cached sets evicted
 * after commit. Sessions are not ended: route keys are not in any token.
 * <p>
 * Every AM replica runs the job: each due row is claimed with
 * {@code FOR UPDATE SKIP LOCKED}, so a key is deleted by exactly one of them.
 */
@Component
public class PendingKeyDeletions {

    private static final Logger LOGGER = LoggerFactory.getLogger(PendingKeyDeletions.class);

    /** One batch per run; the next run takes the rest. */
    private static final int BATCH = 200;

    private static final String CLAIM_DUE = """
            SELECT pd.permission_id, p.permission_key, pd.service FROM t_permission_pending_deletion pd
              JOIN t_permission p ON p.id = pd.permission_id
             WHERE pd.due_at <= now()
             ORDER BY pd.due_at
             LIMIT ?
               FOR UPDATE OF pd SKIP LOCKED""";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final PermissionHolders holders;
    private final EventPublisher events;
    private final RoleEntityRepository roles;
    private final PermissionEntityRepository permissions;

    public PendingKeyDeletions(JdbcTemplate jdbc, TransactionTemplate transactions, PermissionHolders holders,
                               EventPublisher events, RoleEntityRepository roles,
                               PermissionEntityRepository permissions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.holders = holders;
        this.events = events;
        this.roles = roles;
        this.permissions = permissions;
    }

    @Scheduled(fixedDelayString = "${igrp.routes.deletion-check-interval:1m}",
            initialDelayString = "${igrp.routes.deletion-check-interval:1m}")
    public void run() {
        int deleted = deleteDue();
        if (deleted > 0) {
            LOGGER.info("[ROUTES] Deleted {} route key(s) their services no longer declare", deleted);
        }
    }

    /** @return how many keys were deleted */
    public int deleteDue() {
        return transactions.execute(status -> {
            List<Map<String, Object>> due = Objects.requireNonNullElse(jdbc.queryForList(CLAIM_DUE, BATCH), List.of());
            due.forEach(row -> delete(((Number) row.get("permission_id")).intValue(),
                    (String) row.get("permission_key"), (String) row.get("service")));
            return due.size();
        });
    }

    private void delete(int permissionId, String key, String service) {
        holders.evictAfterCommit(permissionId, RolePermissionChangedEvent.CHANGE_KEYS_DELETED);
        // Through the entities, so Envers records the unassignments and the status (audit layer 1).
        for (RoleEntity role : roles.findAllByPermissionId(permissionId)) {
            role.getPermissions().removeIf(permission -> Objects.equals(permission.getId(), permissionId));
            roles.save(role);
        }
        permissions.findById(permissionId).ifPresent(permission -> {
            permission.getDepartments().clear();
            permission.setStatus(Status.DELETED);
            permissions.save(permission);
        });
        permissions.flush();
        // What has no entity path here: links from deleted roles, direct grants, resources and menu items.
        jdbc.update("DELETE FROM t_role_permission WHERE permission = ?", permissionId);
        jdbc.update("DELETE FROM t_service_account_permission_grant WHERE permission_id = ?", permissionId);
        jdbc.update("DELETE FROM t_resource_permission WHERE permission = ?", permissionId);
        jdbc.update("DELETE FROM t_resource_item_permissions WHERE permissions_id = ?", permissionId);
        jdbc.update("DELETE FROM t_permission_pending_deletion WHERE permission_id = ?", permissionId);
        events.publishSettingsAudit(new PermissionDeletedEvent(key, service));
        LOGGER.info("[ROUTES] Deleted {}: {} no longer declares it", key, service);
    }
}
