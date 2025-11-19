package cv.igrp.platform.access_management.users.specs;

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
            spec = spec.and((root, _, cb) -> {
                Join<Object, Object> roleJoin = root.join("roles", JoinType.INNER);
                Join<Object, Object> departmentJoin = roleJoin.join("department", JoinType.INNER);
                Join<Object, Object> applicationJoin = departmentJoin.join("applications", JoinType.INNER);
                return cb.equal(applicationJoin.get("code"), query.getApplicationCode());
            });
        }

        if (query.getDepartmentCode() != null) {
            spec = spec.and((root, _, cb) -> {
                Join<Object, Object> roleJoin = root.join("roles", JoinType.INNER);
                Join<Object, Object> departmentJoin = roleJoin.join("department", JoinType.INNER);
                return cb.equal(departmentJoin.get("code"), query.getDepartmentCode());
            });
        }

        if (query.getName() != null && !query.getName().isEmpty()) {
            spec = spec.and((root, _, cb) -> cb.like(cb.lower(root.get("name")), "%" + query.getName().toLowerCase() + "%"));
        }

        if (query.getId() != null && query.getId() != 0) {
            spec = spec.and((root, _, cb) -> cb.equal(root.get("id"), query.getId()));
        }

        if (query.getEmail() != null && !query.getEmail().isEmpty()) {
            spec = spec.and((root, _, cb) -> cb.like(cb.lower(root.get("email")), "%" + query.getEmail().toLowerCase() + "%"));
        }
        
        // Apply scope
        spec = applyScope(spec, context);

        return spec;
    }

    private Specification<IGRPUserEntity> applyScope(Specification<IGRPUserEntity> spec, ScopeContext context) {
        
        if(!context.isSuperAdmin()) {
            return spec.and((root, _, _) -> {
                Join<Object, Object> roleJoin = root.join("roles", JoinType.INNER);
                Join<Object, Object> departmentJoin = roleJoin.join("department", JoinType.INNER);
                return departmentJoin.get("id").in(context.getDepartmentIds());
            });
        }
        
        return spec;
        
    }


}
