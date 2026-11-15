package cv.igrp.platform.access_management.migration_review.application.dto;

import java.util.List;

/**
 * A migration review suggestion (v3-29, #37): copy the legacy permission's
 * assignments to the route key that named it as its {@code legacyKey}.
 *
 * @param roles       the roles of the key's application holding the legacy permission, with their scopes:
 *                    what confirming copies
 * @param departments the departments of the key's application the legacy permission is linked to: copied too
 */
public record MigrationSuggestionDTO(int id, String key, String keyName, String service, String applicationCode,
                                     String legacyName, String legacyDescription, String status,
                                     List<RoleGrant> roles, List<String> departments) {

    /**
     * A role holding the legacy permission, and the scope it holds it with.
     *
     * @param currentScope the scope the role already holds the key with; confirming keeps it (null: it gets
     *                     {@code scope})
     */
    public record RoleGrant(String departmentCode, String roleCode, String scope, String currentScope) {
    }
}
