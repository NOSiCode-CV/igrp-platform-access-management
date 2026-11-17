package cv.igrp.platform.access_management.shared.infrastructure.persistence.entity;

/**
 * AM's own data visibility on the starter's scope filter (#25). Each condition is the
 * {@code department} strategy for one entity: rows of the caller's visible department subtree,
 * whose codes the permission fetch sends ({@code :igrpDepartments}). AM's entities have no owner
 * or assigned-role column, so {@code own} matches nothing, and {@code by_role} matches only the
 * caller's own roles. {@code all} turns the filter off.
 *
 * <p>Aliases are written out ({@code deduceAliasInjectionPoints = false}) because the
 * conditions hold subqueries.
 */
final class AmScopes {

    private AmScopes() {
    }

    private static final String IN_SUBTREE = " in (:igrpDepartments)";

    private static final String VALID_ASSIGNMENT =
            "(igrp_ura.expires_at is null or igrp_ura.expires_at > current_timestamp)";

    /** The department itself is in the subtree. */
    static final String DEPARTMENT = "(:igrpDepartment = true and {alias}.code" + IN_SUBTREE + ")";

    /** The application is linked to a department of the subtree. */
    static final String APPLICATION = "(:igrpDepartment = true and exists (select 1 from t_department_application igrp_da"
            + " join t_department igrp_d on igrp_d.id = igrp_da.department_id"
            + " where igrp_da.application_id = {alias}.id and igrp_d.code" + IN_SUBTREE + "))";

    /** The role belongs to a department of the subtree; with {@code by_role}, only the caller's own roles there. */
    static final String ROLE = "((:igrpDepartment = true or (:igrpByRole = true and {alias}.code in (:igrpRoles)))"
            + " and exists (select 1 from t_department igrp_d"
            + " where igrp_d.id = {alias}.department and igrp_d.code" + IN_SUBTREE + "))";

    /**
     * The user holds a valid role in a department of the subtree, or holds no valid role at all,
     * so that a department administrator can give a new user their first role.
     */
    static final String USER = "(:igrpDepartment = true and (exists (select 1 from t_user_role_assignment igrp_ura"
            + " join t_role igrp_r on igrp_r.id = igrp_ura.role_id"
            + " join t_department igrp_d on igrp_d.id = igrp_r.department"
            + " where igrp_ura.user_id = {alias}.id and " + VALID_ASSIGNMENT + " and igrp_d.code" + IN_SUBTREE + ")"
            + " or not exists (select 1 from t_user_role_assignment igrp_ura"
            + " where igrp_ura.user_id = {alias}.id and " + VALID_ASSIGNMENT + ")))";
}
