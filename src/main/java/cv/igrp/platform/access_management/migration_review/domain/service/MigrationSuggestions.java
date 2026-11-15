package cv.igrp.platform.access_management.migration_review.domain.service;

import cv.igrp.platform.access_management.migration_review.application.dto.MigrationSuggestionDTO;
import cv.igrp.platform.access_management.migration_review.application.dto.MigrationSuggestionDTO.RoleGrant;
import cv.igrp.platform.access_management.routes.domain.service.PermissionHolders;
import cv.igrp.platform.access_management.security_audit.domain.events.LegacyAssignmentsCopiedEvent;
import cv.igrp.platform.access_management.shared.domain.events.EventPublisher;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The migration review (v3-29, #37; spec "Database migration"). Business
 * permissions are never converted automatically: a registration that names a
 * route's legacy permission ({@code legacyKey}) produces a suggestion, and an
 * administrator decides.
 *
 * <p>Confirming copies, to the route key, the legacy permission's grants to
 * roles (with their scopes) and its department links, of the departments of
 * the key's application only: a service cannot pull in another application's
 * holders by naming their permission. A role that already holds the key keeps
 * its own scope (shown as {@code currentScope}). The legacy permission keeps
 * its assignments, because services still on 0.2 check it. Service-account
 * grants are not copied: service accounts get route keys from their own
 * registration.
 */
@Service
public class MigrationSuggestions {

    /** A suggestion's status. */
    public enum Status {
        PENDING, CONFIRMED, DISMISSED
    }

    /** The change type of the holders' {@code RolePermissionChangedEvent}: keys were added to roles. */
    static final String CHANGE_LEGACY_COPIED = "PERMISSIONS_ADDED";

    private static final String LIST = """
            SELECT s.id, s.status, s.permission_id, s.legacy_permission_id, k.application_id, k.permission_key,
                   k.name AS key_name, k.service, a.code AS application_code, l.name AS legacy_name,
                   l.description AS legacy_description
              FROM t_migration_suggestion s
              JOIN t_permission k ON k.id = s.permission_id
              JOIN t_permission l ON l.id = s.legacy_permission_id
              LEFT JOIN t_application a ON a.id = k.application_id
             WHERE (CAST(? AS varchar) IS NULL OR s.status = ?) AND k.status <> 'DELETED' AND l.status <> 'DELETED'
               AND (CAST(? AS varchar) IS NULL OR a.code = ?)
               AND (CAST(? AS integer) IS NULL OR s.id = ?)
             ORDER BY k.permission_key, l.name""";

    /** The legacy permission's roles in the key's application, with the scope each holds the key with, if any. */
    private static final String LEGACY_ROLES = """
            SELECT d.code AS department_code, r.code AS role_code, rp.scope, cur.scope AS current_scope
              FROM t_role_permission rp
              JOIN t_role r ON r.id = rp.role_id
              JOIN t_department d ON d.id = r.department
              JOIN t_department_application da ON da.department_id = d.id AND da.application_id = ?
              LEFT JOIN t_role_permission cur ON cur.role_id = rp.role_id AND cur.permission = ?
             WHERE rp.permission = ? ORDER BY d.code, r.code""";

    private static final String LEGACY_DEPARTMENTS = """
            SELECT d.code FROM t_permission_department pd
              JOIN t_department d ON d.id = pd.department
              JOIN t_department_application da ON da.department_id = d.id AND da.application_id = ?
             WHERE pd.permission_id = ? ORDER BY d.code""";

    private static final String COPY_ROLES = """
            INSERT INTO t_role_permission (role_id, permission, scope)
            SELECT rp.role_id, ?, rp.scope FROM t_role_permission rp
              JOIN t_role r ON r.id = rp.role_id
              JOIN t_department_application da ON da.department_id = r.department AND da.application_id = ?
             WHERE rp.permission = ?
               AND NOT EXISTS (SELECT 1 FROM t_role_permission e WHERE e.role_id = rp.role_id AND e.permission = ?)""";

    private static final String COPY_DEPARTMENTS = """
            INSERT INTO t_permission_department (permission_id, department)
            SELECT ?, pd.department FROM t_permission_department pd
              JOIN t_department_application da ON da.department_id = pd.department AND da.application_id = ?
             WHERE pd.permission_id = ?
               AND NOT EXISTS (SELECT 1 FROM t_permission_department e WHERE e.permission_id = ? AND e.department = pd.department)""";

    /**
     * Decides a pending suggestion, first: the row lock makes a concurrent
     * decision wait and then find it decided, before any side effect.
     */
    private static final String DECIDE = """
            UPDATE t_migration_suggestion SET status = ?, decided_date = now(), decided_by = ?
             WHERE id = ? AND status = 'PENDING'""";

    private record Row(int id, String status, int keyId, int legacyId, int applicationId, String key, String keyName,
                       String service, String applicationCode, String legacyName, String legacyDescription) {
    }

    private final JdbcTemplate jdbc;
    private final PermissionHolders holders;
    private final EventPublisher events;
    private final AuthenticationHelper authenticationHelper;

    public MigrationSuggestions(JdbcTemplate jdbc, PermissionHolders holders, EventPublisher events,
                                AuthenticationHelper authenticationHelper) {
        this.jdbc = jdbc;
        this.holders = holders;
        this.events = events;
        this.authenticationHelper = authenticationHelper;
    }

    /** The suggestions with this status (PENDING when none is given), of one application or all. */
    @Transactional(readOnly = true)
    public List<MigrationSuggestionDTO> list(String applicationCode, String status) {
        Status wanted = status == null || status.isBlank() ? Status.PENDING : parse(status);
        return rows(wanted.name(), applicationCode, null).stream().map(this::view).toList();
    }

    /** Copies the legacy permission's roles (with scopes) and departments, in the key's application, to the key. */
    @Transactional
    public MigrationSuggestionDTO confirm(int id) {
        Row row = decide(id, Status.CONFIRMED);
        int roles = jdbc.update(COPY_ROLES, row.keyId(), row.applicationId(), row.legacyId(), row.keyId());
        int departments = jdbc.update(COPY_DEPARTMENTS, row.keyId(), row.applicationId(), row.legacyId(),
                row.keyId());
        if (roles > 0 || departments > 0) {
            // The roles that now hold the key: their sets are evicted (and their sessions end, as on any grant).
            holders.evictAfterCommit(row.keyId(), CHANGE_LEGACY_COPIED);
        }
        events.publishSettingsAudit(new LegacyAssignmentsCopiedEvent(row.key(), row.legacyName(), roles, departments));
        return view(row(id));
    }

    /** Copies nothing; the suggestion leaves the pending list. */
    @Transactional
    public MigrationSuggestionDTO dismiss(int id) {
        decide(id, Status.DISMISSED);
        return view(row(id));
    }

    /** Records the decision, or refuses: 404 unknown, 409 already decided (by someone else, maybe meanwhile). */
    private Row decide(int id, Status status) {
        Row row = row(id);
        if (jdbc.update(DECIDE, status.name(), authenticationHelper.getSub(), id) != 1) {
            throw IgrpResponseStatusException.of(HttpStatus.CONFLICT,
                    "Suggestion " + id + " is already " + row(id).status());
        }
        return row;
    }

    private static Status parse(String status) {
        try {
            return Status.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw IgrpResponseStatusException.badRequest("Unknown status " + status + "; expected "
                    + Arrays.toString(Status.values()));
        }
    }

    private Row row(int id) {
        return rows(null, null, id).stream().findFirst()
                .orElseThrow(() -> IgrpResponseStatusException.notFound("Suggestion " + id + " not found"));
    }

    private List<Row> rows(String status, String applicationCode, Integer id) {
        return jdbc.query(LIST, (rs, i) -> new Row(rs.getInt("id"), rs.getString("status"),
                        rs.getInt("permission_id"), rs.getInt("legacy_permission_id"), rs.getInt("application_id"),
                        rs.getString("permission_key"), rs.getString("key_name"), rs.getString("service"),
                        rs.getString("application_code"), rs.getString("legacy_name"),
                        rs.getString("legacy_description")),
                status, status, applicationCode, applicationCode, id, id);
    }

    private MigrationSuggestionDTO view(Row row) {
        List<RoleGrant> roles = jdbc.query(LEGACY_ROLES, (rs, i) -> new RoleGrant(rs.getString("department_code"),
                        rs.getString("role_code"), rs.getString("scope"), rs.getString("current_scope")),
                row.applicationId(), row.keyId(), row.legacyId());
        List<String> departments = jdbc.queryForList(LEGACY_DEPARTMENTS, String.class, row.applicationId(),
                row.legacyId());
        return new MigrationSuggestionDTO(row.id(), row.key(), row.keyName(), row.service(), row.applicationCode(),
                row.legacyName(), row.legacyDescription(), row.status(), roles, departments);
    }
}
