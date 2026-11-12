package cv.igrp.platform.access_management.shared.application.constants;

import java.util.Collection;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * Row visibility of a permission assigned to a role (v3-13, #21; spec "Data
 * scopes"). Stored in {@code t_role_permission.scope} and returned by the
 * permission fetch, where the starter turns it into a query condition.
 */
public enum PermissionScope {

    /** Every row; the starter skips the condition. */
    ALL("all"),
    /** Rows the caller owns. */
    OWN("own"),
    /** Rows assigned to one of the caller's roles. */
    BY_ROLE("by_role"),
    /** Rows in the caller's visible department subtree. */
    DEPARTMENT("department");

    /** The scope of a new assignment, and of every non-root one at the migration. */
    public static final PermissionScope DEFAULT = DEPARTMENT;

    private final String code;

    PermissionScope(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** The scope for a stored or requested code, exactly as written. */
    public static PermissionScope fromCode(String code) {
        for (PermissionScope scope : values()) {
            if (scope.code.equals(code)) {
                return scope;
            }
        }
        throw new IllegalArgumentException("Unknown scope '" + code + "'; expected all, own, by_role or department");
    }

    /**
     * A key's scopes combined with OR, as the Redis {@code :scopes} hash holds
     * them: {@code all} alone when present, otherwise the distinct codes sorted
     * and comma-separated ({@code by_role,own}). One scope is just its code.
     */
    public static String union(Collection<PermissionScope> scopes) {
        if (scopes.contains(ALL)) {
            return ALL.code;
        }
        return scopes.stream().distinct().map(PermissionScope::code)
                .sorted(Comparator.naturalOrder()).collect(Collectors.joining(","));
    }
}
