package cv.igrp.platform.access_management.permission.domain.service;

import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.domain.models.Permission;
import cv.igrp.platform.access_management.shared.domain.validation.ResourceValidationResponse;
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
        Permission existingPermission = new Permission();
        existingPermission.setName(existingPermissionName);

        Application application = new Application();
        application.setId(1);
        application.setPermissions(List.of(existingPermission));

        PermissionDTO newPermissionDTO = new PermissionDTO();
        newPermissionDTO.setName("read_user");
        newPermissionDTO.setApplicationId(1);

        // When
        ResourceValidationResponse response =
                PermissionValidator.validatePermissionName(newPermissionDTO, application);

        // Then
        assertFalse(response.isValid());
        assertEquals(1, response.getFailureMessage().size());
    }

    @Test
    void shouldReturnValidWhenPermissionNameDoesNotExist() {
        // Given
        Application application = new Application();
        application.setPermissions(Collections.emptyList());

        PermissionDTO dto = new PermissionDTO();
        dto.setName("NEW_PERMISSION");
        dto.setApplicationId(1);

        // When
        ResourceValidationResponse response = PermissionValidator.validatePermissionName(dto, application);

        // Then
        assertTrue(response.isValid());
    }
}