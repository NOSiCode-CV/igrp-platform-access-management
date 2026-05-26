package cv.igrp.platform.access_management.users.specs;

import cv.igrp.platform.access_management.m2m.application.commands.GetUsersForBusinessCommand;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.spring.Scoped;
import cv.igrp.platform.access_management.shared.security.ScopeContext;
import cv.igrp.platform.access_management.users.application.queries.GetUsersQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class UserSpecificationBuilder {

    /**
     * Join {@code IGRPUserEntity → userRoleAssignments → role} and return the
     * Role join, filtered to non-expired assignments. There is no direct
     * {@code roles} association on {@link IGRPUserEntity} — the public
     * {@code getRoles()} method on the entity is a Java-side stream filter
     * over {@code userRoleAssignments}. Every JPA criteria path that previously
     * did {@code root.join("roles", ...)} must traverse the
     * {@code userRoleAssignments} collection first.
     */
    private static Join<Object, Object> joinActiveRole(Root<IGRPUserEntity> root,
                                                       JoinType joinType,
                                                       CriteriaBuilder cb,
                                                       java.util.List<Predicate> extraPredicates) {
        Join<Object, Object> uraJoin = root.join("userRoleAssignments", joinType);
        // Mirror IGRPUserEntity.getRoles(): filter expired assignments out.
        extraPredicates.add(cb.or(
                cb.isNull(uraJoin.get("expiresAt")),
                cb.greaterThan(uraJoin.get("expiresAt"), cb.literal(java.time.LocalDateTime.now()))
        ));
        return uraJoin.join("role", joinType);
    }

    @Scoped
    public Specification<IGRPUserEntity> buildSpecification(GetUsersQuery query, ScopeContext context) {
        Specification<IGRPUserEntity> spec = Specification.anyOf();

        if (query.getApplicationCode() != null) {
            spec = spec.and((root, criteriaQuery, cb) -> {
                java.util.List<Predicate> preds = new java.util.ArrayList<>();
                Join<Object, Object> roleJoin = joinActiveRole(root, JoinType.INNER, cb, preds);
                Join<Object, Object> departmentJoin = roleJoin.join("department", JoinType.INNER);
                Join<Object, Object> applicationJoin = departmentJoin.join("applications", JoinType.INNER);
                preds.add(cb.equal(applicationJoin.get("code"), query.getApplicationCode()));
                return cb.and(preds.toArray(Predicate[]::new));
            });
        }

        if (query.getDepartmentCode() != null) {
            spec = spec.and((root, criteriaQuery, cb) -> {
                java.util.List<Predicate> preds = new java.util.ArrayList<>();
                Join<Object, Object> roleJoin = joinActiveRole(root, JoinType.INNER, cb, preds);
                Join<Object, Object> departmentJoin = roleJoin.join("department", JoinType.INNER);
                preds.add(cb.equal(departmentJoin.get("code"), query.getDepartmentCode()));
                return cb.and(preds.toArray(Predicate[]::new));
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

        // Restrict the listing to fully provisioned accounts. TEMPORARY users
        // are mid-onboarding (Phase G3 invite flow) and shouldn't appear in
        // admin UIs until they accept the invitation; DELETED users are
        // soft-deletes that must never resurface in normal listings.
        spec = spec.and((root, criteriaQuery, cb) ->
                root.get("status").in(Status.ACTIVE, Status.INACTIVE)
        );

        // Apply scope
        spec = applyScope(spec, context);

        return spec;
    }

    public Specification<IGRPUserEntity> buildSpecification(GetUsersForBusinessCommand query) {

        Specification<IGRPUserEntity> spec = Specification.anyOf();

        if (query.getApplicationCode() != null) {
            spec = spec.and((root, criteriaQuery, cb) -> {
                java.util.List<Predicate> preds = new java.util.ArrayList<>();
                Join<Object, Object> roleJoin = joinActiveRole(root, JoinType.INNER, cb, preds);
                Join<Object, Object> departmentJoin = roleJoin.join("department", JoinType.INNER);
                Join<Object, Object> applicationJoin = departmentJoin.join("applications", JoinType.INNER);
                preds.add(cb.equal(applicationJoin.get("code"), query.getApplicationCode()));
                return cb.and(preds.toArray(Predicate[]::new));
            });
        }

        if (query.getDepartmentCode() != null) {
            spec = spec.and((root, criteriaQuery, cb) -> {
                java.util.List<Predicate> preds = new java.util.ArrayList<>();
                Join<Object, Object> roleJoin = joinActiveRole(root, JoinType.INNER, cb, preds);
                Join<Object, Object> departmentJoin = roleJoin.join("department", JoinType.INNER);
                preds.add(cb.equal(departmentJoin.get("code"), query.getDepartmentCode()));
                return cb.and(preds.toArray(Predicate[]::new));
            });
        }

        if (query.getRoleCode() != null) {
            spec = spec.and((root, criteriaQuery, cb) -> {
                java.util.List<Predicate> preds = new java.util.ArrayList<>();
                Join<Object, Object> roleJoin = joinActiveRole(root, JoinType.INNER, cb, preds);
                preds.add(cb.equal(roleJoin.get("code"), query.getRoleCode()));
                return cb.and(preds.toArray(Predicate[]::new));
            });
        }

        if (query.getPermissionName() != null) {
            spec = spec.and((root, criteriaQuery, cb) -> {
                java.util.List<Predicate> preds = new java.util.ArrayList<>();
                Join<Object, Object> roleJoin = joinActiveRole(root, JoinType.INNER, cb, preds);
                Join<Object, Object> permissionJoin = roleJoin.join("permissions", JoinType.INNER);
                preds.add(cb.equal(permissionJoin.get("name"), query.getPermissionName()));
                return cb.and(preds.toArray(Predicate[]::new));
            });
        }

        if (query.getDepartmentCode() != null && query.isIncludeChildrenDepartments()) {
            spec = spec.or((root, criteriaQuery, cb) -> {
                java.util.List<Predicate> preds = new java.util.ArrayList<>();
                Join<Object, Object> roleJoin = joinActiveRole(root, JoinType.LEFT, cb, preds);
                Join<Object, Object> departmentJoin = roleJoin.join("department", JoinType.LEFT);
                Join<Object, Object> parentDepartmentJoin = departmentJoin.join("parentId", JoinType.LEFT);
                preds.add(cb.equal(parentDepartmentJoin.get("code"), query.getDepartmentCode()));
                return cb.and(preds.toArray(Predicate[]::new));
            });
        }

        if (query.getRoleCode() != null && query.isIncludeChildrenRoles()) {
            spec = spec.or((root, criteriaQuery, cb) -> {
                java.util.List<Predicate> preds = new java.util.ArrayList<>();
                Join<Object, Object> roleJoin = joinActiveRole(root, JoinType.LEFT, cb, preds);
                Join<Object, Object> parentRoleJoin = roleJoin.join("parent", JoinType.LEFT);
                preds.add(cb.equal(parentRoleJoin.get("code"), query.getRoleCode()));
                return cb.and(preds.toArray(Predicate[]::new));
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
                java.util.List<Predicate> preds = new java.util.ArrayList<>();
                Join<Object, Object> roleJoin = joinActiveRole(root, JoinType.INNER, cb, preds);
                Join<Object, Object> departmentJoin = roleJoin.join("department", JoinType.INNER);
                preds.add(departmentJoin.get("id").in(context.getDepartmentIds()));
                return cb.and(preds.toArray(Predicate[]::new));
            });
        }

        return spec;

    }


}
