package cv.igrp.platform.access_management.shared.specs;

import cv.igrp.platform.access_management.department.application.queries.GetDepartmentsQuery;
import cv.igrp.platform.access_management.department.specs.DepartmentSpecificationBuilder;
import cv.igrp.platform.access_management.m2m.application.commands.GetDepartmentForBusinessCommand;
import cv.igrp.platform.access_management.m2m.application.commands.GetUsersForBusinessCommand;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.users.application.queries.GetUsersQuery;
import cv.igrp.platform.access_management.users.specs.UserSpecificationBuilder;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The filters of the department and user listings walk the attributes they name. Scope is no
 * longer theirs (#25: the starter's filter on the entities), so only the query's own filters are
 * left here.
 */
class SpecificationFilterPathsTest {

    @SuppressWarnings("unchecked")
    private static <T> Root<T> root() {
        return mock(Root.class, RETURNS_DEEP_STUBS);
    }

    private static <T> void apply(Specification<T> spec, Root<T> root, CriteriaBuilder cb) {
        spec.toPredicate(root, mock(CriteriaQuery.class), cb);
    }

    @Test
    @DisplayName("The department listing filters on status and on the parent department")
    void departmentListing() {
        Root<DepartmentEntity> root = root();
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);

        apply(new DepartmentSpecificationBuilder().buildSpecification(
                new GetDepartmentsQuery("Finance", DepartmentStatus.ACTIVE.name(), "FIN", "ROOT")), root, cb);

        verify(root, atLeastOnce()).get("status");
        verify(root).join("parentId");
    }

    @Test
    @DisplayName("The business department lookup joins the parent department")
    void businessDepartmentLookup() {
        Root<DepartmentEntity> root = root();
        GetDepartmentForBusinessCommand command = new GetDepartmentForBusinessCommand();
        command.setParentCode("ROOT");
        command.setActiveOnly(true);

        apply(new DepartmentSpecificationBuilder().buildSpecification(command), root,
                mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS));

        verify(root).join("parentId", JoinType.INNER);
    }

    @Test
    @DisplayName("The user listing reaches the department through the user's role, for both filters")
    @SuppressWarnings("unchecked")
    void userListing() {
        Root<IGRPUserEntity> root = root();
        GetUsersQuery query = new GetUsersQuery();
        query.setApplicationCode("APP");
        query.setDepartmentCode("FIN");

        apply(new UserSpecificationBuilder().buildSpecification(query), root,
                mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS));

        Join<Object, Object> role = root.join("userRoleAssignments", JoinType.INNER).join("role", JoinType.INNER);
        verify(role, atLeastOnce()).join(eq("department"), any(JoinType.class));
    }

    @Test
    @DisplayName("The business user lookup reaches the department, and its parent for child departments")
    @SuppressWarnings("unchecked")
    void businessUserLookup() {
        Root<IGRPUserEntity> root = root();
        GetUsersForBusinessCommand command = new GetUsersForBusinessCommand();
        command.setApplicationCode("APP");
        command.setDepartmentCode("FIN");
        command.setIncludeChildrenDepartments(true);

        apply(new UserSpecificationBuilder().buildSpecification(command), root,
                mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS));

        Join<Object, Object> innerRole = root.join("userRoleAssignments", JoinType.INNER).join("role", JoinType.INNER);
        Join<Object, Object> leftRole = root.join("userRoleAssignments", JoinType.LEFT).join("role", JoinType.LEFT);
        verify(innerRole, atLeastOnce()).join("department", JoinType.INNER);
        verify(leftRole).join("department", JoinType.LEFT);
    }
}
