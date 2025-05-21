package cv.igrp.platform.access_management.permission.domain.service;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.domain.models.Permission;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PermissionMapperTest {

    private final PermissionMapper underTest = new PermissionMapper();


    @Test
    void itShouldStartContext() {
        assertNotNull(underTest);
    }

    @Test
    void itShouldMapAllFieldsCorrectly_FromPermissionToDTO() {
        // Given
        int permissionId = 1;
        String permissionName = "ACCESS_DASHBOARD";
        String permissionDescription = "Allows dashboard access";
        Status permissionStatus = Status.ACTIVE;

        Application application = new Application();
        application.setId(10);

        Permission permission = new Permission();
        permission.setId(permissionId);
        permission.setName(permissionName);
        permission.setDescription(permissionDescription);
        permission.setStatus(permissionStatus);
        permission.setApplication(application);

        // When
        PermissionDTO result = underTest.mapToDTO(permission);

        // Then
        assertNotNull(result);
        assertEquals(permissionId, result.getId());
        assertEquals(permissionName, result.getName());
        assertEquals(permissionDescription, result.getDescription());
        assertEquals(permissionStatus, result.getStatus());
        assertEquals(application.getId(), result.getApplicationId());
    }

    @Test
    void itShouldMapAllFieldsCorrectly_FromDTOToPermission() {
        // Given
        int applicationId = 10;
        String name = "ACCESS_DASHBOARD";
        String description = "Allows dashboard access";
        Status status = Status.INACTIVE;

        PermissionDTO dto = new PermissionDTO();
        dto.setName(name);
        dto.setDescription(description);
        dto.setStatus(status);
        dto.setApplicationId(applicationId);

        Application application = new Application();
        application.setId(applicationId);

        // When
        Permission permission = underTest.mapDtoToEntity(dto, application);

        // Then
        assertNotNull(permission);
        assertEquals(name, permission.getName());
        assertEquals(description, permission.getDescription());
        assertEquals(status, permission.getStatus());
        assertEquals(application, permission.getApplication());
    }
}