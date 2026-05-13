package cv.igrp.platform.access_management.users.specs;

import cv.igrp.platform.access_management.m2m.application.commands.GetUsersForBusinessCommand;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.spring.Scoped;
import cv.igrp.platform.access_management.shared.security.ScopeContext;
import cv.igrp.platform.access_management.users.application.queries.GetUsersQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class UserSpecificationBuilder {

    @Scoped
    public Specification<IGRPUserEntity> buildSpecification(GetUsersQuery query, ScopeContext context) {
        Specification<IGRPUserEntity> spec = Specification.anyOf();

        if (query.getApplicationCode() != null) {
            spec = spec.and((root, criteriaQuery, cb) -> {
                Join<Object, Object> roleJoin = root.join("roles", JoinType.INNER);
                Join<Object, Object> departmentJoin = roleJoin.join("department", JoinType.INNER);
                Join<Object, Object> applicationJoin = departmentJoin.join("applications", JoinType.INNER);
                return cb.equal(applicationJoin.get("code"), query.getApplicationCode());
            });
        }

        if (query.getDepartmentCode() != null) {
            spec = spec.and((root, criteriaQuery, cb) -> {
                Join<Object, Object> roleJoin = root.join("roles", JoinType.INNER);
                Join<Object, Object> departmentJoin = roleJoin.join("department", JoinType.INNER);
                return cb.equal(departmentJoin.get("code"), query.getDepartmentCode());
            });
        }

        if (query.getName() != null && !query.getName().isEmpty()) {
            spec = spec.and((root, criteriaQuery, cb) -> cb.like(cb.lower(root.get("name")), "%" + query.getName().toLowerCase() + "%"));
        }

        if (query.getId() != null && !query.getId().isBlank()) {
            spec = spec.and((root, criteriaQuery, cb) -> cb.equal(root.get("id"), query.getId()));
        }

        if (query.getEmail() != null && !query.getEmail().isEmpty()) {
            spec = spec.and((root, criteriaQuery, cb) -> cb.like(cb.lower(root.get("email")), "%" + query.getEmail().toLowerCase() + "%"));
        }
        
        // Apply scope
        spec = applyScope(spec, context);

        return spec;
    }

    public Specification<IGRPUserEntity> buildSpecification(GetUsersForBusinessCommand query) {

        Specification<IGRPUserEntity> spec = Specification.anyOf();

        if (query.getApplicationCode() != null) {
            spec = spec.and((root, criteriaQuery, cb) -> {
                Join<Object, Object> roleJoin = root.join("roles", JoinType.INNER);
                Join<Object, Object> departmentJoin = roleJoin.join("department", JoinType.INNER);
                Join<Object, Object> applicationJoin = departmentJoin.join("applications", JoinType.INNER);
                return cb.equal(applicationJoin.get("code"), query.getApplicationCode());
            });
        }

        if (query.getDepartmentCode() != null) {
            spec = spec.and((root, criteriaQuery, cb) -> {
                Join<Object, Object> roleJoin = root.join("roles", JoinType.INNER);
                Join<Object, Object> departmentJoin = roleJoin.join("department", JoinType.INNER);
                return cb.equal(departmentJoin.get("code"), query.getDepartmentCode());
            });
        }

        if (query.getRoleCode() != null) {
            spec = spec.and((root, criteriaQuery, cb) -> {
                Join<Object, Object> roleJoin = root.join("roles", JoinType.INNER);
                return cb.equal(roleJoin.get("code"), query.getRoleCode());
            });
        }

        if (query.getPermissionName() != null) {
            spec = spec.and((root, criteriaQuery, cb) -> {
                Join<Object, Object> roleJoin = root.join("roles", JoinType.INNER);
                Join<Object, Object> permissionJoin = roleJoin.join("permissions", JoinType.INNER);
                return cb.equal(permissionJoin.get("name"), query.getPermissionName());
            });
        }

        if (query.getDepartmentCode() != null && query.isIncludeChildrenDepartments()) {
            spec = spec.or((root, criteriaQuery, cb) -> {
                Join<Object, Object> roleJoin = root.join("roles", JoinType.LEFT);
                Join<Object, Object> departmentJoin = roleJoin.join("department", JoinType.LEFT);
                Join<Object, Object> parentDepartmentJoin = departmentJoin.join("parentId", JoinType.LEFT);
                return cb.equal(parentDepartmentJoin.get("code"), query.getDepartmentCode());
            });
        }

        if (query.getRoleCode() != null && query.isIncludeChildrenRoles()) {
            spec = spec.or((root, criteriaQuery, cb) -> {
                Join<Object, Object> roleJoin = root.join("roles", JoinType.LEFT);
                Join<Object, Object> parentRoleJoin = roleJoin.join("parent", JoinType.LEFT);
                return cb.equal(parentRoleJoin.get("code"), query.getRoleCode());
            });
        }

        if (query.getGetUsersForBusinessRequest() != null && !query.getGetUsersForBusinessRequest().isEmpty()) {
            if(query.getGetUsersForBusinessRequest().stream().allMatch(it -> it.contains("@"))) {
                spec = spec.and((root, criteriaQuery, cb) -> cb.in(root.get("email")).value(query.getGetUsersForBusinessRequest()));
            } else if (query.getGetUsersForBusinessRequest().stream().allMatch(it -> it.matches("\\d+"))) {
                spec = spec.and((root, criteriaQuery, cb) -> cb.in(root.get("id")).value(query.getGetUsersForBusinessRequest()));
            } else {
                spec = spec.and((root, _, cb) -> cb.in(root.get("username")).value(query.getGetUsersForBusinessRequest()));
            }
        }

        if (query.isActiveOnly()) {
            spec = spec.and((root, criteriaQuery, cb) -> cb.equal(root.get("status"), Status.ACTIVE));
        }

        return spec;
    }

    private Specification<IGRPUserEntity> applyScope(Specification<IGRPUserEntity> spec, ScopeContext context) {
        
        if(!context.isSuperAdmin()) {
            return spec.and((root, criteriaQuery, cb) -> {
                Join<Object, Object> roleJoin = root.join("roles", JoinType.INNER);
                Join<Object, Object> departmentJoin = roleJoin.join("department", JoinType.INNER);
                return departmentJoin.get("id").in(context.getDepartmentIds());
            });
        }
        
        return spec;
        
    }


}
