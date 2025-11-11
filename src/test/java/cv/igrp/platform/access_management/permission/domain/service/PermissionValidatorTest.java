package cv.igrp.platform.access_management.permission.domain.service;

import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.domain.validation.ResourceValidationResponse;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PermissionValidatorTest {

    @Test
    void shouldReturnInvalidWhenPermissionNameAlreadyExists() {
        // Given
        String existingPermissionName = "READ_USER";
        PermissionEntity existingPermission = new PermissionEntity();
        existingPermission.setName(existingPermissionName);
        existingPermission.setStatus(Status.ACTIVE);

        DepartmentEntity department = new DepartmentEntity();
        department.setId(1);
        department.setCode("DEPT");
        department.setPermissions(List.of(existingPermission));
        department.setStatus(DepartmentStatus.ACTIVE);

        PermissionDTO newPermissionDTO = new PermissionDTO();
        newPermissionDTO.setName("read_user");
        newPermissionDTO.setDepartmentCode("DEPT");
        newPermissionDTO.setStatus(Status.ACTIVE);

        // When
        ResourceValidationResponse response =
                PermissionValidator.validatePermissionName(newPermissionDTO, department);

        // Then
        assertFalse(response.isValid());
        assertEquals(1, response.getFailureMessage().size());
    }

    @Test
    void shouldReturnValidWhenPermissionNameDoesNotExist() {
        // Given
        DepartmentEntity department = new DepartmentEntity();
        department.setId(1);
        department.setCode("DEPT");
        department.setPermissions(Collections.emptyList());

        PermissionDTO dto = new PermissionDTO();
        dto.setName("NEW_PERMISSION");
        dto.setDepartmentCode("DEPT");

        // When
        ResourceValidationResponse response = PermissionValidator.validatePermissionName(dto, department);

        // Then
        assertTrue(response.isValid());
    }
}