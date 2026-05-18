package cv.igrp.platform.access_management.department.specs;

import cv.igrp.platform.access_management.department.application.queries.GetDepartmentsQuery;
import cv.igrp.platform.access_management.m2m.application.commands.GetDepartmentForBusinessCommand;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpErrorCode;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.spring.Scoped;
import cv.igrp.platform.access_management.shared.security.ScopeContext;
import jakarta.persistence.criteria.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static cv.igrp.platform.access_management.shared.infrastructure.service.ConfigurationService.IGRP_DEPARTMENT;

@Component
public class DepartmentSpecificationBuilder {

    Logger logger = LoggerFactory.getLogger(DepartmentSpecificationBuilder.class);

    @Scoped
    public Specification<DepartmentEntity> buildSpecification(GetDepartmentsQuery query, ScopeContext context) {
        return (root, criteriaQuery, cb) -> {
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

    public Specification<DepartmentEntity> buildSpecification(GetDepartmentForBusinessCommand query) {

        return (root, criteriaQuery, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            if (query.getParentCode() != null) {
                Join<DepartmentEntity, DepartmentEntity> parentJoin = root.join("parentId", JoinType.INNER);
                predicates.add(cb.equal(parentJoin.get("code"), query.getParentCode()));
            }

            if (!query.isIncludeChildrenDepartments() && query.getParentCode() == null) {
                predicates.add(cb.isNull(root.get("parentId")));
            }

            if (query.getGetDepartmentForBusinessRequest() != null && !query.getGetDepartmentForBusinessRequest().isEmpty()) {
                predicates.add(cb.in(root.get("code")).value(query.getGetDepartmentForBusinessRequest()));
            }

            if (query.isActiveOnly()) {
                predicates.add(cb.equal(root.get("status"), DepartmentStatus.ACTIVE));
            }

            predicates.add(cb.notEqual(root.get("status"), DepartmentStatus.DELETED));
            predicates.add(cb.notEqual(root.get("code"), IGRP_DEPARTMENT));

            return cb.and(predicates.toArray(new Predicate[0]));

        };

    }

    private void applyScope(List<Predicate> predicates, Root<DepartmentEntity> root, ScopeContext context) {
        Expression<Integer> deptIdPath = root.get("id");

        // Add scope filter if available
        if (!context.isSuperAdmin()) {
            predicates.add(deptIdPath.in(context.getDepartmentIds()));
        }

        System.out.println("[[DEBUG]] Department IDs in Scope: " + context.getDepartmentIds());

    }

    private DepartmentStatus resolveDepartmentStatus(String status) {
        try {
            return DepartmentStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid status provided: '{}'", status);
            throw IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_DEPARTMENT_INVALID_STATUS, status);
        }
    }

}
