package cv.igrp.platform.access_management.shared.infrastructure.service;

import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import cv.igrp.platform.access_management.shared.security.RequestScopeCache;
import cv.igrp.platform.access_management.shared.security.SubjectParser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static cv.igrp.platform.access_management.shared.infrastructure.service.ConfigurationService.SUPER_ADMIN_ROLE;

@Service
public class ScopeService {

    private final AuthenticationHelper authenticationHelper;
    private final RequestScopeCache cache;
    private final DepartmentEntityRepository departmentRepository;
    private final ApplicationEntityRepository applicationRepository;
    private final RoleEntityRepository roleRepository;
    private final JdbcTemplate jdbcTemplate;

    public ScopeService(
            AuthenticationHelper authenticationHelper,
            RequestScopeCache cache,
            DepartmentEntityRepository departmentRepository,
            ApplicationEntityRepository applicationRepository,
            RoleEntityRepository roleRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.authenticationHelper = authenticationHelper;
        this.cache = cache;
        this.departmentRepository = departmentRepository;
        this.applicationRepository = applicationRepository;
        this.roleRepository = roleRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Checks if the authenticated user is a superadmin.
     */
    public boolean isSuperAdmin() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null)
            return false;

        // Extract sub (external_id)
        String sub = authenticationHelper.getSub();

        // Check in DB if the user has the superadmin role
        String sql = """
                SELECT 1 FROM t_user_role_assignment ura
                JOIN t_role r ON r.id = ura.role_id
                JOIN t_user u ON u.id = ura.user_id
                WHERE u.id = ? AND r.code = ?
                  AND (ura.expires_at IS NULL OR ura.expires_at > NOW())
                LIMIT 1
                """;

        var results = jdbcTemplate.query(sql, (rs, rowNum) -> 1, SubjectParser.parseUserSubjectOrThrow(sub), SUPER_ADMIN_ROLE);

        return !results.isEmpty();

    }

    /**
     * Returns an ActorPrincipal describing the current authenticated actor.
     * Supports:
     *  - JWT authenticated users
     *  - M2M authenticated machine clients
     */
    public ActorPrincipal getActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null) {
            throw new IllegalStateException("No authentication available in security context");
        }

        // Extract sub
        String sub = authenticationHelper.getSub();

        // Extract roles (from JWT authorities for now, but the flag will come from DB)
        Set<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        boolean isSuperAdmin = this.isSuperAdmin();

        return new ActorPrincipal(
                SubjectParser.parseUserSubjectOrThrow(sub),
                roles,
                isSuperAdmin,
                auth.getPrincipal()
        );
    }

    /**
     * DEPARTMENT ACCESS SCOPE LOGIC
     *
     * <p>For a non-super-admin, the visible-departments set is rooted at the
     * department owning the user's <strong>currently active role</strong> in
     * {@code t_user.active_role_id}, expanded with all descendant departments.
     * The previous implementation parsed JWT authority strings of the form
     * {@code DEPT_CODE.ROLE_CODE}, which silently produced an empty set when
     * the IdP did not emit that exact format — leaving users with a role in
     * a department unable to see that department.
     */
    public Set<Integer> getVisibleDepartmentIds() {
        if (cache.getVisibleDepartments() != null)
            return cache.getVisibleDepartments();

        if (this.isSuperAdmin()) {
            Set<Integer> all = new HashSet<>();
            departmentRepository.findAll().forEach(d -> all.add(d.getId()));
            cache.setVisibleDepartments(all);
            return all;
        }

        Set<Integer> ids = new HashSet<>();
        Integer activeDeptId = departmentRepository.findActiveRoleDepartmentId(this.getActor().id());
        if (activeDeptId != null) {
            ids.add(activeDeptId);
            ids.addAll(resolveDescendants(activeDeptId));
        }

        cache.setVisibleDepartments(ids);
        return ids;
    }

    /**
     * Recursively resolves children departments
     */
    private Set<Integer> resolveDescendants(Integer parentId) {
        Set<Integer> result = new HashSet<>();
        Set<Integer> children = departmentRepository.findDirectChildren(parentId);

        for (Integer c : children) {
            result.add(c);
            result.addAll(resolveDescendants(c));
        }
        return result;
    }

    /**
     * APPLICATION ACCESS SCOPE LOGIC
     */
    public Set<Integer> getVisibleApplicationIds() {
        if (cache.getVisibleApplications() != null)
            return cache.getVisibleApplications();

        Set<Integer> deptIds = getVisibleDepartmentIds();

        Set<Integer> appIds = applicationRepository.findByDepartmentIds(deptIds);

        cache.setVisibleApplications(appIds);
        return appIds;
    }

    /**
     * ROLE ACCESS SCOPE LOGIC
     *
     * <p>Visible roles are all non-deleted roles that belong to any visible
     * department (which, for non-super-admins, is the active role's department
     * subtree). Derived from {@link #getVisibleDepartmentIds()} so any future
     * adjustment to how departments are scoped flows through automatically.
     */
    public Set<Integer> getVisibleRoleIds() {
        if (cache.getVisibleRoles() != null)
            return cache.getVisibleRoles();

        if (this.isSuperAdmin()) {
            Set<Integer> all = new HashSet<>();
            roleRepository.findAll().forEach(d -> all.add(d.getId()));
            cache.setVisibleRoles(all);
            return all;
        }

        Set<Integer> deptIds = getVisibleDepartmentIds();
        Set<Integer> ids = deptIds.isEmpty()
                ? new HashSet<>()
                : roleRepository.findIdsByDepartmentIdIn(deptIds);

        cache.setVisibleRoles(ids);
        return ids;
    }

    public record ActorPrincipal(
            String id,
            Set<String> roles,
            boolean superAdmin,
            Object rawPrincipal
    ) {}

}
