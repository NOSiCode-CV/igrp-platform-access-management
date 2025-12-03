package cv.igrp.platform.access_management.role.specs;

import cv.igrp.platform.access_management.department.application.queries.GetRolesQuery;
import cv.igrp.platform.access_management.m2m.application.commands.GetRolesForBusinessCommand;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.spring.Scoped;
import cv.igrp.platform.access_management.shared.security.ScopeContext;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import static cv.igrp.platform.access_management.shared.infrastructure.service.ConfigurationService.SUPER_ADMIN_ROLE;

@Component
public class RoleSpecificationBuilder {

    /**
     * Builds a dynamic JPA {@link Specification} based on optional code and code filters.
     *
     * @param query the query object containing departmentCode and code filters (optional)
     * @param context the scope context containing the current user's departments, applications and roles (optional)
     * @return a {@link Specification} representing the composed query filters
     */
    @Scoped
    public Specification<RoleEntity> buildSpecification(GetRolesQuery query, ScopeContext context) {

        Specification<RoleEntity> specs = Specification.allOf();

        specs = specs.and((root, _, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("department").get("code"), query.getCode())
        );

        if(query.getRoleCode() != null && !query.getRoleCode().isBlank()) {
            specs = specs.and((root, _, criteriaBuilder) ->
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("code")), "%" + query.getRoleCode().toLowerCase() + "%")
            );
        }

        specs = specs.and((root, _, _) ->
                root.get("status").in(Status.ACTIVE, Status.INACTIVE)
        );

        specs = specs.and((root, _, criteriaBuilder) ->
                criteriaBuilder.notEqual(root.get("code"), SUPER_ADMIN_ROLE)
        );

        // Apply scope
        specs = applyScope(specs, context);

        return specs;

    }

    public Specification<RoleEntity> buildSpecification(GetRolesForBusinessCommand query) {

        Specification<RoleEntity> specs = Specification.allOf();

        if (query.getParentCode() != null) {
            specs = specs.and((root, _, cb) ->
                    cb.equal(root.get("parent").get("code"), query.getParentCode())
            );
        }

        if (!query.isIncludeChildrenRoles() && query.getParentCode() == null) {
            specs = specs.and((root, _, cb) ->
                    cb.isNull(root.get("parent"))
            );
        }

        if (query.getGetRolesForBusinessRequest() != null && !query.getGetRolesForBusinessRequest().isEmpty()) {
            specs = specs.and((root, _, cb) ->
                    cb.in(root.get("code")).value(query.getGetRolesForBusinessRequest())
            );
        }

        if (query.isActiveOnly()) {
            specs = specs.and((root, _, cb) ->
                    cb.equal(root.get("status"), Status.ACTIVE)
            );
        }

        specs = specs.and((root, _, criteriaBuilder) ->
                criteriaBuilder.notEqual(root.get("status"), Status.DELETED)
        );

        specs = specs.and((root, _, criteriaBuilder) ->
                criteriaBuilder.notEqual(root.get("code"), SUPER_ADMIN_ROLE)
        );

        return specs;

    }

    private Specification<RoleEntity> applyScope(Specification<RoleEntity> spec, ScopeContext context) {

        if(!context.isSuperAdmin()) {
            return spec.and((root, _, cb) ->
                    root.get("id").in(context.getRoleIds())
            );
        }

        return spec;
    }

}
