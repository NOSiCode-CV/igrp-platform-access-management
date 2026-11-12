package cv.igrp.platform.access_management.shared.infrastructure.service;

import cv.igrp.platform.access_management.permission_fetch.domain.service.PermissionSetEviction;
import cv.igrp.platform.access_management.security_audit.domain.events.RoleAssignedToUserEvent;
import cv.igrp.platform.access_management.security_audit.domain.events.RoleUnassignedFromUserEvent;
import cv.igrp.platform.access_management.shared.domain.events.EventPublisher;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpErrorCode;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static cv.igrp.platform.access_management.shared.infrastructure.service.ConfigurationService.IGRP_DEPARTMENT;
import static cv.igrp.platform.access_management.shared.infrastructure.service.ConfigurationService.SUPER_ADMIN_ROLE;

/**
 * The single root (v3-12, #20; spec "Super-admin (root)"): the user whose
 * email is {@code IGRP_SUPERADMIN_EMAIL}. On every startup {@link #sync} makes
 * that user ACTIVE (creating it, with no invitation, if none exists) and the
 * only holder of the super-admin role: every other user and service account
 * loses it, with their permission keys, and each change is audited. A
 * previous root keeps root power until its current access token expires
 * (accepted by the spec). No endpoint assigns the role: see
 * {@link #refuseIfRoot}.
 */
@Component
public class RootUser {

    private static final Logger LOGGER = LoggerFactory.getLogger(RootUser.class);
    private static final String SYSTEM_USER = "system";

    private static final String ROOT_ROLE_ID = """
            SELECT r.id FROM t_role r JOIN t_department d ON d.id = r.department
             WHERE r.code = ? AND d.code = ?""";

    /** The configured user, an ACTIVE row first when an email is shared by mistake. */
    private static final String BY_EMAIL = """
            SELECT id, status FROM t_user WHERE lower(email) = lower(?) AND status <> 'DELETED'
             ORDER BY CASE status WHEN 'ACTIVE' THEN 0 ELSE 1 END, created_date LIMIT 1""";

    /** Users holding the super-admin code (the role or a look-alike), except root's own assignment. */
    private static final String HOLDERS_BUT_ROOT = """
            SELECT DISTINCT ura.user_id FROM t_user_role_assignment ura JOIN t_role r ON r.id = ura.role_id
             WHERE r.code = ? AND NOT (ura.user_id = ? AND ura.role_id = ?)""";

    private static final String DELETE_ASSIGNMENTS_BUT_ROOT = """
            DELETE FROM t_user_role_assignment
             WHERE role_id IN (SELECT id FROM t_role WHERE code = ?) AND NOT (user_id = ? AND role_id = ?)""";

    private static final String DELETE_APPLICATION_ROLES_BUT_ROOT = """
            DELETE FROM t_user_application_role
             WHERE role_id IN (SELECT id FROM t_role WHERE code = ?) AND NOT (user_id = ? AND role_id = ?)""";

    private static final String CLEAR_ACTIVE_ROLES_BUT_ROOT = """
            UPDATE t_user SET active_role_id = NULL, last_modified_by = ?, last_modified_date = now()
             WHERE active_role_id IN (SELECT id FROM t_role WHERE code = ?) AND NOT (id = ? AND active_role_id = ?)""";

    private static final String SERVICE_ACCOUNT_HOLDERS = """
            SELECT DISTINCT sara.service_account_id::text FROM t_service_account_role_assignment sara
              JOIN t_role r ON r.id = sara.role_id WHERE r.code = ?""";

    private static final String DELETE_SERVICE_ACCOUNT_ASSIGNMENTS = """
            DELETE FROM t_service_account_role_assignment WHERE role_id IN (SELECT id FROM t_role WHERE code = ?)""";

    private final JdbcTemplate jdbc;
    private final EventPublisher events;
    private final PermissionSetEviction eviction;

    public RootUser(JdbcTemplate jdbc, EventPublisher events, PermissionSetEviction eviction) {
        this.jdbc = jdbc;
        this.events = events;
        this.eviction = eviction;
    }

    /** Whether the role is the super-admin role itself: its code, in the iGRP department. Grants root. */
    public static boolean isRootRole(RoleEntity role) {
        return hasRootCode(role) && role.getDepartment() != null
                && IGRP_DEPARTMENT.equals(role.getDepartment().getCode());
    }

    /**
     * Whether the role carries the super-admin code, in any department. Never
     * assignable: a look-alike in another department must not be created or
     * given either.
     */
    public static boolean hasRootCode(RoleEntity role) {
        return role != null && SUPER_ADMIN_ROLE.equals(role.getCode());
    }

    /** 400 when one of the roles carries the super-admin code: root comes from configuration only. */
    public static void refuseIfRoot(Collection<RoleEntity> roles) {
        if (roles != null && roles.stream().anyMatch(RootUser::hasRootCode)) {
            throw notAssignable();
        }
    }

    /** As {@link #refuseIfRoot}, on role codes, before anything is looked up or changed. */
    public static void refuseIfRootCode(Collection<String> roleCodes) {
        if (roleCodes != null && roleCodes.stream().anyMatch(SUPER_ADMIN_ROLE::equals)) {
            throw notAssignable();
        }
    }

    private static IgrpResponseStatusException notAssignable() {
        return IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_ROLE_SUPERADMIN_NOT_ASSIGNABLE);
    }

    /** Whether the user is root now: holds the super-admin role itself. */
    public boolean isRoot(String userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM t_user_role_assignment ura
                                 JOIN t_role r ON r.id = ura.role_id JOIN t_department d ON d.id = r.department
                                WHERE ura.user_id = ? AND r.code = ? AND d.code = ?)""",
                Boolean.class, userId, SUPER_ADMIN_ROLE, IGRP_DEPARTMENT));
    }

    /** 400: root is changed through IGRP_SUPERADMIN_EMAIL, never by taking its role or disabling it. */
    public void refuseIfRootUser(String userId) {
        if (isRoot(userId)) {
            throw IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_ROOT_PROTECTED);
        }
    }

    /**
     * Makes the user with this email the one root.
     *
     * @param email      {@code IGRP_SUPERADMIN_EMAIL}
     * @param externalId {@code IGRP_SUPERADMIN_USER_EXTERNAL_ID}, optional: the username of a root created now
     * @return the root's user id
     */
    @Transactional
    public String sync(String email, String externalId) {
        List<Integer> roleIds = list(ROOT_ROLE_ID, Integer.class, SUPER_ADMIN_ROLE, IGRP_DEPARTMENT);
        if (roleIds.isEmpty()) {
            throw new IllegalStateException("The super-admin role " + SUPER_ADMIN_ROLE + " does not exist");
        }
        int roleId = roleIds.getFirst();
        String rootId = activeUser(email.trim(), externalId);
        List<String> changed = new ArrayList<>();
        if (grantRoot(rootId, roleId)) {
            // Its keys were computed without root.
            changed.add(rootId);
        }
        changed.addAll(demoteUsers(rootId, roleId));
        changed.addAll(demoteServiceAccounts());
        eviction.evictUsersAfterCommit(changed);
        return rootId;
    }

    private String activeUser(String email, String externalId) {
        List<Map<String, Object>> found = Objects.requireNonNullElse(jdbc.queryForList(BY_EMAIL, email), List.of());
        if (found.isEmpty()) {
            String id = UUID.randomUUID().toString();
            jdbc.update("""
                    INSERT INTO t_user (id, name, username, email, status,
                                        created_by, created_date, last_modified_by, last_modified_date)
                    VALUES (?, 'iGRP Super Admin', ?, ?, 'ACTIVE', ?, now(), ?, now())""",
                    id, username(email, externalId), email, SYSTEM_USER, SYSTEM_USER);
            LOGGER.info("[Root] Created the root user {} for {}", id, email);
            return id;
        }
        String id = (String) found.getFirst().get("id");
        if (!"ACTIVE".equals(found.getFirst().get("status"))) {
            jdbc.update("""
                    UPDATE t_user SET status = 'ACTIVE', last_modified_by = ?, last_modified_date = now()
                     WHERE id = ?""", SYSTEM_USER, id);
            LOGGER.info("[Root] Made the root user {} ACTIVE (was {})", id, found.getFirst().get("status"));
        }
        return id;
    }

    /**
     * The external id when set and free, else the email when free, else the
     * email with a short suffix: usernames are unique, DELETED rows included.
     */
    private String username(String email, String externalId) {
        if (externalId != null && !externalId.isBlank() && usernameFree(externalId)) {
            return externalId;
        }
        return usernameFree(email) ? email : email + "~" + UUID.randomUUID().toString().substring(0, 8);
    }

    private boolean usernameFree(String username) {
        return !Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM t_user WHERE username = ?)", Boolean.class, username));
    }

    /** @return whether root's role or active role changed */
    private boolean grantRoot(String rootId, int roleId) {
        int inserted = jdbc.update("""
                INSERT INTO t_user_role_assignment (user_id, role_id, assigned_at)
                SELECT ?, ?, now()
                 WHERE NOT EXISTS (SELECT 1 FROM t_user_role_assignment WHERE user_id = ? AND role_id = ?)""",
                rootId, roleId, rootId, roleId);
        int pinned = jdbc.update("""
                UPDATE t_user SET active_role_id = ?, last_modified_by = ?, last_modified_date = now()
                 WHERE id = ? AND active_role_id IS DISTINCT FROM ?""", roleId, SYSTEM_USER, rootId, roleId);
        if (inserted > 0) {
            events.publishSettingsAudit(new RoleAssignedToUserEvent(SUPER_ADMIN_ROLE, rootId));
            LOGGER.info("[Root] User {} is root", rootId);
        }
        return inserted > 0 || pinned > 0;
    }

    /**
     * Every user but root loses the role, and everyone, root included, loses
     * any look-alike (the super-admin code in another department), with the
     * active roles and per-application choices that point at them.
     */
    private List<String> demoteUsers(String rootId, int roleId) {
        List<String> others = list(HOLDERS_BUT_ROOT, String.class, SUPER_ADMIN_ROLE, rootId, roleId);
        jdbc.update(DELETE_ASSIGNMENTS_BUT_ROOT, SUPER_ADMIN_ROLE, rootId, roleId);
        jdbc.update(DELETE_APPLICATION_ROLES_BUT_ROOT, SUPER_ADMIN_ROLE, rootId, roleId);
        // Also users who never held it: an active role is not a grant, but must not point at root.
        jdbc.update(CLEAR_ACTIVE_ROLES_BUT_ROOT, SYSTEM_USER, SUPER_ADMIN_ROLE, rootId, roleId);
        for (String userId : others) {
            events.publishSettingsAudit(new RoleUnassignedFromUserEvent(SUPER_ADMIN_ROLE, userId));
            LOGGER.warn("[Root] User {} is no longer root: {} is configured as root", userId, rootId);
        }
        return others;
    }

    private List<String> demoteServiceAccounts() {
        List<String> accounts = list(SERVICE_ACCOUNT_HOLDERS, String.class, SUPER_ADMIN_ROLE);
        if (!accounts.isEmpty()) {
            jdbc.update(DELETE_SERVICE_ACCOUNT_ASSIGNMENTS, SUPER_ADMIN_ROLE);
            accounts.forEach(account -> {
                events.publishSettingsAudit(new RoleUnassignedFromUserEvent(SUPER_ADMIN_ROLE, account));
                LOGGER.warn("[Root] Service account {} is no longer root", account);
            });
        }
        return accounts;
    }

    /** {@code queryForList} is declared nullable; an absent result is an empty list. */
    private <T> List<T> list(String sql, Class<T> type, Object... args) {
        return Objects.requireNonNullElse(jdbc.queryForList(sql, type, args), List.of());
    }
}
