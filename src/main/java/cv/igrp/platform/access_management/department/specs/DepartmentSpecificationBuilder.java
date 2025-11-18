package cv.igrp.platform.access_management.department.specs;

import cv.igrp.platform.access_management.department.application.queries.GetDepartmentsQuery;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.spring.Scoped;
import cv.igrp.platform.access_management.shared.security.ScopeContext;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static cv.igrp.platform.access_management.shared.infrastructure.service.ConfigurationService.IGRP_DEPARTMENT;

@Component
public class DepartmentSpecificationBuilder {

    Logger logger = LoggerFactory.getLogger(DepartmentSpecificationBuilder.class);

    @Scoped
    public Specification<DepartmentEntity> buildSpecification(GetDepartmentsQuery query, ScopeContext context) {
        return (root, _, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (query.getName() != null && !query.getName().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + query.getName().toLowerCase() + "%"));
            }

            if (query.getStatus() != null) {
                DepartmentStatus departmentStatus = resolveDepartmentStatus(query.getStatus());
                predicates.add(cb.equal(root.get("status"), departmentStatus));
            }

            if (query.getCode() != null) {
                predicates.add(cb.equal(root.get("code"), query.getCode()));
            }

            if (query.getParentCode() != null) {
                Join<DepartmentEntity, DepartmentEntity> parentJoin = root.join("parentId");
                predicates.add(cb.equal(parentJoin.get("code"), query.getParentCode()));
            }

            // Exclude deleted departments
            predicates.add(cb.notEqual(root.get("status"), DepartmentStatus.DELETED));
            predicates.add(cb.notEqual(root.get("code"), IGRP_DEPARTMENT));

            // Apply scope
            applyScope(predicates, root, context);

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private void applyScope(List<Predicate> predicates, Root<DepartmentEntity> root, ScopeContext context) {
        Expression<Integer> deptIdPath = root.get("id");

        // Add scope filter if available
        if(!context.isSuperAdmin()) {
            predicates.add(deptIdPath.in(context.getDepartmentIds()));
        }

        System.out.println("[[DEBUG]] Department IDs in Scope: " + context.getDepartmentIds());

    }

    private DepartmentStatus resolveDepartmentStatus(String status) {
        try {
            return DepartmentStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid status provided: '{}'", status);
            throw IgrpResponseStatusException.of(
                    HttpStatus.BAD_REQUEST,
                    "Invalid department status",
                    "No department status found with name: " + status
            );
        }
    }

}
