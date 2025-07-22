package cv.igrp.platform.access_management.permission.domain.service;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
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

        ApplicationEntity application = new ApplicationEntity();
        application.setId(10);

        PermissionEntity permission = new PermissionEntity();
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
        assertEquals(application.getCode(), result.getApplicationCode());
    }

    @Test
    void itShouldSetStatusToActive_When_NotProvided() {
        // Given
        String applicationCode = "app";
        String name = "ACCESS_DASHBOARD";
        String description = "Allows dashboard access";

        PermissionDTO dto = new PermissionDTO();
        dto.setName(name);
        dto.setDescription(description);
        dto.setStatus(null);
        dto.setApplicationCode(applicationCode);

        ApplicationEntity application = new ApplicationEntity();
        application.setCode(applicationCode);

        // When
        PermissionEntity permission = underTest.mapDtoToEntity(dto, application);

        // Then
        assertNotNull(permission);
        assertEquals(name, permission.getName());
        assertEquals(description, permission.getDescription());
        assertEquals(Status.ACTIVE, permission.getStatus());
        assertEquals(application, permission.getApplication());
    }

    @Test
    void itShouldMapAllFieldsCorrectly_FromDTOToPermission() {
        // Given
        String applicationCode = "app";
        String name = "ACCESS_DASHBOARD";
        String description = "Allows dashboard access";
        Status status = Status.INACTIVE;

        PermissionDTO dto = new PermissionDTO();
        dto.setName(name);
        dto.setDescription(description);
        dto.setStatus(status);
        dto.setApplicationCode(applicationCode);

        ApplicationEntity application = new ApplicationEntity();
        application.setCode(applicationCode);

        // When
        PermissionEntity permission = underTest.mapDtoToEntity(dto, application);

        // Then
        assertNotNull(permission);
        assertEquals(name, permission.getName());
        assertEquals(description, permission.getDescription());
        assertEquals(status, permission.getStatus());
        assertEquals(application, permission.getApplication());
    }
}