package cv.igrp.platform.access_management.permission.domain.service;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
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

        DepartmentEntity department = new DepartmentEntity();
        department.setId(10);
        department.setCode("DEPT");

        PermissionEntity permission = new PermissionEntity();
        permission.setId(permissionId);
        permission.setName(permissionName);
        permission.setDescription(permissionDescription);
        permission.setStatus(permissionStatus);
        permission.setDepartment(department);

        // When
        PermissionDTO result = underTest.mapToDTO(permission);

        // Then
        assertNotNull(result);
        assertEquals(permissionId, result.getId());
        assertEquals(permissionName, result.getName());
        assertEquals(permissionDescription, result.getDescription());
        assertEquals(permissionStatus, result.getStatus());
        assertEquals(department.getCode(), result.getDepartmentCode());
    }

    @Test
    void itShouldSetStatusToActive_When_NotProvided() {
        // Given
        String departmentCode = "DEPT";
        String name = "ACCESS_DASHBOARD";
        String description = "Allows dashboard access";

        PermissionDTO dto = new PermissionDTO();
        dto.setName(name);
        dto.setDescription(description);
        dto.setStatus(null);
        dto.setDepartmentCode(departmentCode);

        DepartmentEntity department = new DepartmentEntity();
        department.setCode(departmentCode);

        // When
        PermissionEntity permission = underTest.mapDtoToEntity(dto, department);

        // Then
        assertNotNull(permission);
        assertEquals(name, permission.getName());
        assertEquals(description, permission.getDescription());
        assertEquals(Status.ACTIVE, permission.getStatus());
        assertEquals(department, permission.getDepartment());
    }

    @Test
    void itShouldMapAllFieldsCorrectly_FromDTOToPermission() {
        // Given
        String departmentCode = "DEPT";
        String name = "ACCESS_DASHBOARD";
        String description = "Allows dashboard access";
        Status status = Status.INACTIVE;

        PermissionDTO dto = new PermissionDTO();
        dto.setName(name);
        dto.setDescription(description);
        dto.setStatus(status);
        dto.setDepartmentCode(departmentCode);

        DepartmentEntity department = new DepartmentEntity();
        department.setCode(departmentCode);

        // When
        PermissionEntity permission = underTest.mapDtoToEntity(dto, department);

        // Then
        assertNotNull(permission);
        assertEquals(name, permission.getName());
        assertEquals(description, permission.getDescription());
        assertEquals(status, permission.getStatus());
        assertEquals(department, permission.getDepartment());
    }
}