package cv.igrp.platform.access_management.role.domain.service;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.validation.ResourceValidationResponse;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Role Validator Tests")
class RoleValidatorTest {


    @Test
    void shouldReturnValidResponse_WhenRoleNameIsUnique() {
        // Given
        RoleDTO newRole = new RoleDTO();
        newRole.setCode("Admin");

        DepartmentEntity department = new DepartmentEntity();
        department.setRoles(List.of());

        // When
        ResourceValidationResponse response = RoleValidator.validateRoleDto(newRole, department);

        // Then
        assertTrue(response.isValid());
        assertTrue(response.getFailureMessage().isEmpty());
    }

    @Test
    void shouldReturnInvalidResponse_WhenRoleNameAlreadyExists() {
        // Given
        RoleEntity existingRole = new RoleEntity();
        String roleCode = "Admin";
        existingRole.setCode(roleCode);
        existingRole.setStatus(Status.ACTIVE);

        DepartmentEntity department = new DepartmentEntity();
        department.setId(1);
        department.setRoles(List.of(existingRole));

        RoleDTO newRole = new RoleDTO();
        newRole.setCode(roleCode);
        newRole.setStatus(Status.ACTIVE);

        // When
        ResourceValidationResponse response = RoleValidator.validateRoleDto(newRole, department);

        // Then
        assertFalse(response.isValid());
        assertEquals(1, response.getFailureMessage().size());
    }
}