package cv.igrp.platform.access_management.users.specs;

import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.spring.Scoped;
import cv.igrp.platform.access_management.shared.security.ScopeContext;
import cv.igrp.platform.access_management.users.application.commands.GetUsersCommand;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class UserSpecificationBuilder {

    @Scoped
    public Specification<IGRPUserEntity> buildSpecification(GetUsersCommand command, ScopeContext context) {
        Specification<IGRPUserEntity> spec = Specification.anyOf();

        if (command.getApplicationCode() != null) {
            spec = spec.and((root, _, cb) -> {
                Join<Object, Object> roleJoin = root.join("roles", JoinType.INNER);
                Join<Object, Object> departmentJoin = roleJoin.join("department", JoinType.INNER);
                Join<Object, Object> applicationJoin = departmentJoin.join("applications", JoinType.INNER);
                return cb.equal(applicationJoin.get("code"), command.getApplicationCode());
            });
        }

        if (command.getDepartmentCode() != null) {
            spec = spec.and((root, _, cb) -> {
                Join<Object, Object> roleJoin = root.join("roles", JoinType.INNER);
                Join<Object, Object> departmentJoin = roleJoin.join("department", JoinType.INNER);
                return cb.equal(departmentJoin.get("code"), command.getDepartmentCode());
            });
        }

        if (command.getName() != null && !command.getName().isEmpty()) {
            spec = spec.and((root, _, cb) -> cb.like(cb.lower(root.get("name")), "%" + command.getName().toLowerCase() + "%"));
        }

        if (command.getId() != null && command.getId() != 0) {
            spec = spec.and((root, _, cb) -> cb.equal(root.get("id"), command.getId()));
        }

        if (command.getEmail() != null && !command.getEmail().isEmpty()) {
            spec = spec.and((root, _, cb) -> cb.like(cb.lower(root.get("email")), "%" + command.getEmail().toLowerCase() + "%"));
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
