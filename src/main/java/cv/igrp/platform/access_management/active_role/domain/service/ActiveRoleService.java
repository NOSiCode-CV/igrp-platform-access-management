package cv.igrp.platform.access_management.active_role.domain.service;

import cv.igrp.platform.access_management.active_role.application.dto.ActiveRoleDTO;
import cv.igrp.platform.access_management.active_role.application.dto.ActiveRoleDTO.RoleRefDTO;
import cv.igrp.platform.access_management.active_role.application.dto.ActiveRoleDTO.Source;
import cv.igrp.platform.access_management.active_role.domain.events.ActiveRoleChangedEvent;
import cv.igrp.platform.access_management.security_audit.domain.events.ActiveRoleSwitchedEvent;
import cv.igrp.platform.access_management.shared.domain.events.EventPublisher;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.service.ConfigurationService;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import cv.igrp.platform.access_management.shared.security.SubjectParser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Active role per application (v3-05, spec "Active role per application").
 *
 * <p>A role is valid for a user in an application when the user holds it (an
 * active role, assignment not expired) and its department is linked to the
 * application, the same path AM uses to list the user's applications. The
 * stored choice is honoured only while it stays valid; otherwise the only
 * valid role is the default, and several valid roles give no active role.
 * Root's active role is the super-admin role, in every application.
 *
 * <p>{@link #resolve} is the server-side answer other code reuses (the
 * permission fetch, #14). The global {@code t_user.active_role_id} is
 * untouched: the 0.2 surface keeps reading it until #38.
 */
@Service
public class ActiveRoleService {

    private static final RowMapper<RoleRow> ROLE_ROW = (rs, i) -> new RoleRow(
            rs.getInt("id"), rs.getString("code"), rs.getString("name"), rs.getString("department_code"));

    private static final String VALID_ROLES = """
            SELECT DISTINCT r.id, r.code, r.name, d.code AS department_code FROM t_role r
              JOIN t_department d ON d.id = r.department
              JOIN t_department_application da ON da.department_id = d.id
              JOIN t_user_role_assignment ura ON ura.role_id = r.id
             WHERE ura.user_id = ? AND da.application_id = ?
               AND r.status = 'ACTIVE' AND d.status = 'ACTIVE'
               AND (ura.expires_at IS NULL OR ura.expires_at > localtimestamp)
             ORDER BY department_code, r.code""";

    private static final String SUPER_ADMIN_ROLE = """
            SELECT r.id, r.code, r.name, d.code AS department_code FROM t_role r
              JOIN t_department d ON d.id = r.department
             WHERE r.code = ? AND d.code = ? AND r.status = 'ACTIVE'""";

    private static final String IS_ROOT = """
            SELECT EXISTS (SELECT 1 FROM t_user_role_assignment ura
                             JOIN t_role r ON r.id = ura.role_id
                             JOIN t_department d ON d.id = r.department
                            WHERE ura.user_id = ? AND r.code = ? AND d.code = ? AND r.status = 'ACTIVE'
                              AND (ura.expires_at IS NULL OR ura.expires_at > localtimestamp))""";

    private static final String STORED_ROLE =
            "SELECT role_id FROM t_user_application_role WHERE user_id = ? AND application_id = ?";

    /** Atomic: two first switches at once both succeed instead of racing on the primary key. */
    private static final String STORE_CHOICE = """
            INSERT INTO t_user_application_role (user_id, application_id, role_id, updated_at)
            VALUES (?, ?, ?, now())
            ON CONFLICT (user_id, application_id) DO UPDATE SET role_id = EXCLUDED.role_id, updated_at = now()""";

    private final JdbcTemplate jdbc;
    private final AuthenticationHelper authenticationHelper;
    private final EventPublisher audit;
    private final ApplicationEventPublisher events;

    public ActiveRoleService(JdbcTemplate jdbc,
                             AuthenticationHelper authenticationHelper,
                             EventPublisher audit,
                             ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.authenticationHelper = authenticationHelper;
        this.audit = audit;
        this.events = events;
    }

    /** A user's active role in an application: stored, default or super-admin; empty when there is none. */
    public record ResolvedActiveRole(int roleId, String code, String departmentCode, Source source) {
    }

    /**
     * The active role {@code userId} acts under in {@code applicationCode}, or
     * empty when they have none (several valid roles and no choice, or none
     * valid). 404 when the application does not exist or is not active.
     */
    @Transactional(readOnly = true)
    public Optional<ResolvedActiveRole> resolve(String userId, String applicationCode) {
        int applicationId = applicationId(applicationCode);
        if (isRoot(userId)) {
            RoleRow root = superAdminRole();
            return Optional.of(root.resolved(Source.SUPER_ADMIN));
        }
        List<RoleRow> valid = validRoles(userId, applicationId);
        return effectiveRole(valid, storedValidRole(userId, applicationId, valid));
    }

    /** The caller's active role in the application, or its default, with the roles they can pick. */
    @Transactional(readOnly = true)
    public ActiveRoleDTO current(String applicationCode) {
        String userId = currentUserId();
        int applicationId = applicationId(applicationCode);
        if (isRoot(userId)) {
            RoleRow root = superAdminRole();
            return new ActiveRoleDTO(applicationCode, root.ref(), Source.SUPER_ADMIN, List.of(root.ref()));
        }
        List<RoleRow> valid = validRoles(userId, applicationId);
        return view(applicationCode, valid, storedValidRole(userId, applicationId, valid));
    }

    /** Stores the caller's active role in the application; audited in this transaction. */
    @Transactional
    public ActiveRoleDTO switchTo(String applicationCode, String roleCode, String departmentCode) {
        String userId = currentUserId();
        int applicationId = applicationId(applicationCode);
        if (isRoot(userId)) {
            throw IgrpResponseStatusException.badRequest(
                    "The super-admin's active role is always " + ConfigurationService.SUPER_ADMIN_ROLE);
        }
        List<RoleRow> valid = validRoles(userId, applicationId);
        RoleRow chosen = chosenRole(valid, applicationCode, roleCode, departmentCode);
        RoleRow stored = storedValidRole(userId, applicationId, valid);
        if (stored != null && stored.id() == chosen.id()) {
            return view(applicationCode, valid, stored);
        }

        // The role the user was acting under, stored or default, for the audit row.
        RoleRow acting = stored != null || valid.size() != 1 ? stored : valid.getFirst();
        String previous = acting == null ? null : label(acting);
        jdbc.update(STORE_CHOICE, userId, applicationId, chosen.id());
        audit.publishSettingsAudit(new ActiveRoleSwitchedEvent(label(chosen), applicationCode, previous));
        events.publishEvent(new ActiveRoleChangedEvent(userId, applicationCode));
        return view(applicationCode, valid, chosen);
    }

    private static RoleRow chosenRole(List<RoleRow> valid, String applicationCode, String roleCode,
                                      String departmentCode) {
        boolean anyDepartment = departmentCode == null || departmentCode.isBlank();
        List<RoleRow> matches = valid.stream()
                .filter(r -> r.code().equals(roleCode))
                .filter(r -> anyDepartment || r.departmentCode().equals(departmentCode))
                .toList();
        if (matches.isEmpty()) {
            throw IgrpResponseStatusException.badRequest(
                    "Role " + roleCode + " is not valid for you in application " + applicationCode);
        }
        if (matches.size() > 1) {
            throw IgrpResponseStatusException.of(HttpStatus.CONFLICT, "Role " + roleCode
                    + " exists in several departments of application " + applicationCode + "; send departmentCode");
        }
        return matches.getFirst();
    }

    /** The stored choice when valid, else the only valid role, else empty. */
    private static Optional<ResolvedActiveRole> effectiveRole(List<RoleRow> valid, RoleRow stored) {
        if (stored != null) {
            return Optional.of(stored.resolved(Source.STORED));
        }
        if (valid.size() == 1) {
            return Optional.of(valid.getFirst().resolved(Source.DEFAULT));
        }
        return Optional.empty();
    }

    /** @param stored the stored, still valid choice; null when there is none */
    private static ActiveRoleDTO view(String applicationCode, List<RoleRow> valid, RoleRow stored) {
        List<RoleRefDTO> available = valid.stream().map(RoleRow::ref).toList();
        if (stored != null) {
            return new ActiveRoleDTO(applicationCode, stored.ref(), Source.STORED, available);
        }
        if (valid.size() == 1) {
            return new ActiveRoleDTO(applicationCode, valid.getFirst().ref(), Source.DEFAULT, available);
        }
        return new ActiveRoleDTO(applicationCode, null, Source.NONE, available);
    }

    /** "department/role": role codes are unique only within a department. */
    private static String label(RoleRow role) {
        return role.departmentCode() + "/" + role.code();
    }

    /** The stored choice when it is still valid, else null. */
    private RoleRow storedValidRole(String userId, int applicationId, List<RoleRow> valid) {
        List<Integer> stored = integers(STORED_ROLE, userId, applicationId);
        if (stored.isEmpty()) {
            return null;
        }
        int roleId = stored.getFirst();
        return valid.stream().filter(r -> r.id() == roleId).findFirst().orElse(null);
    }

    private List<RoleRow> validRoles(String userId, int applicationId) {
        return jdbc.query(VALID_ROLES, ROLE_ROW, userId, applicationId);
    }

    private boolean isRoot(String userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(IS_ROOT, Boolean.class,
                userId, ConfigurationService.SUPER_ADMIN_ROLE, ConfigurationService.IGRP_DEPARTMENT));
    }

    private RoleRow superAdminRole() {
        List<RoleRow> roles = jdbc.query(SUPER_ADMIN_ROLE, ROLE_ROW,
                ConfigurationService.SUPER_ADMIN_ROLE, ConfigurationService.IGRP_DEPARTMENT);
        if (roles.isEmpty()) {
            throw IgrpResponseStatusException.internalServerError("Super-admin role missing",
                    ConfigurationService.SUPER_ADMIN_ROLE + " is not an active role of "
                            + ConfigurationService.IGRP_DEPARTMENT);
        }
        return roles.getFirst();
    }

    private int applicationId(String applicationCode) {
        List<Integer> ids = integers("SELECT id FROM t_application WHERE code = ? AND status = 'ACTIVE'",
                applicationCode);
        if (ids.isEmpty()) {
            throw IgrpResponseStatusException.notFound("Application " + applicationCode + " not found");
        }
        return ids.getFirst();
    }

    /** {@code queryForList} is declared nullable; an absent result is an empty list. */
    private List<Integer> integers(String sql, Object... args) {
        return Objects.requireNonNullElse(jdbc.queryForList(sql, Integer.class, args), List.of());
    }

    private String currentUserId() {
        return SubjectParser.parseUserSubjectOrThrow(authenticationHelper.getSub());
    }

    /** A role valid for the user in one application. */
    private record RoleRow(int id, String code, String name, String departmentCode) {

        RoleRefDTO ref() {
            return new RoleRefDTO(code, name, departmentCode);
        }

        ResolvedActiveRole resolved(Source source) {
            return new ResolvedActiveRole(id, code, departmentCode, source);
        }
    }
}
