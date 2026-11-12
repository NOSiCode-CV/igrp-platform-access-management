package cv.igrp.platform.access_management.role.domain.service;

import cv.igrp.platform.access_management.shared.application.constants.PermissionScope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reads and writes {@code t_role_permission.scope} (#21). The role's
 * permissions are a JPA many-to-many, which does not map the column, so the
 * scope goes through JDBC; rows Hibernate inserts get the column default,
 * {@link PermissionScope#DEFAULT}.
 */
@Component
public class RolePermissionScopes {

    private static final String BY_PERMISSION_ID =
            "SELECT permission, scope FROM t_role_permission WHERE role_id = ?";

    private static final String UPDATE =
            "UPDATE t_role_permission SET scope = ? WHERE role_id = ? AND permission = ?";

    private final JdbcTemplate jdbc;

    public RolePermissionScopes(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The scope of each of the role's permissions, by permission id. */
    public Map<Integer, PermissionScope> byPermissionId(int roleId) {
        return jdbc.query(BY_PERMISSION_ID,
                        (rs, i) -> Map.entry(rs.getInt(1), PermissionScope.fromCode(rs.getString(2))), roleId)
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /** Sets the scope of one assignment the role holds; the caller has checked it exists. */
    public void set(int roleId, int permissionId, PermissionScope scope) {
        int updated = jdbc.update(UPDATE, scope.code(), roleId, permissionId);
        if (updated != 1) {
            throw new IllegalStateException(
                    "Role " + roleId + " does not hold permission " + permissionId + " (" + updated + " rows)");
        }
    }
}
