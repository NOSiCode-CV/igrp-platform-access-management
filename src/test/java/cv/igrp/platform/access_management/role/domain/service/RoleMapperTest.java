package cv.igrp.platform.access_management.role.domain.service;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.CodeDescriptionDTO;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RoleMapperTest {

    @InjectMocks
    private RoleMapper underTest;

    @Test
    void itShouldStartContext() {
        assertNotNull(underTest);
    }

    @Test
    void itShouldMapRoleToDto_WhenParentIsNull() {
        // Given
        RoleEntity role = new RoleEntity();
        int roleId = 1;
        String roleName = "admin";
        role.setId(roleId);
        role.setName(roleName);
        role.setPermissions(new HashSet<>());
        String roleDescription = "Developer";
        role.setDescription(roleDescription);
        role.setStatus(Status.ACTIVE);

        DepartmentEntity department = new DepartmentEntity();
        int departmentId = 10;
        String departmentCode = "HR";
        department.setId(departmentId);
        department.setCode(departmentCode);
        role.setDepartment(department);

        role.setParent(null);

        // When
        RoleDTO result = underTest.mapToDto(role);

        // Then
        assertNotNull(result);
        assertEquals(roleId, result.getId());
        assertEquals(roleName, result.getName());
        assertEquals(roleDescription, result.getDescription());
        assertEquals(departmentCode, result.getDepartment().getCode());
        assertEquals(Status.ACTIVE, result.getStatus());
        assertNull(result.getParent(), "Expected parentName to be null when role has no parent");
    }

    @Test
    void itShouldMapRoleToDto_WhenParentIsPresent() {
        // Given
        int roleId = 10;
        String roleCode = "admin";
        int parentRoleId = 1;
        String parentRoleCode = "developer";
        int departmentId = 10;
        String departmentCode = "HR";

        RoleEntity role = new RoleEntity();
        RoleEntity parentRole = new RoleEntity();
        parentRole.setId(parentRoleId);
        parentRole.setCode(parentRoleCode);
        parentRole.setPermissions(new HashSet<>());
        role.setId(roleId);
        role.setCode(roleCode);
        role.setPermissions(new HashSet<>());
        String roleDescription = "Developer";
        role.setDescription(roleDescription);
        role.setStatus(Status.ACTIVE);

        DepartmentEntity department = new DepartmentEntity();
        department.setId(departmentId);
        department.setCode(departmentCode);
        role.setDepartment(department);

        role.setParent(parentRole);

        // When
        RoleDTO result = underTest.mapToDto(role);

        // Then
        assertNotNull(result);
        assertEquals(roleId, result.getId());
        assertEquals(roleCode, result.getCode());
        assertEquals(roleDescription, result.getDescription());
        assertEquals(departmentCode, result.getDepartment().getCode());
        assertEquals(Status.ACTIVE, result.getStatus());
        assertNotNull(result.getParent().getCode());
        assertEquals(parentRoleCode, result.getParent().getCode());
    }

    @Test
    void itShouldMapAllFieldsCorrectly_FromRoleToDto() {
        // Given
        int roleId = 1;
        String roleCode = "admin";
        int departmentId = 20;
        String departmentCode = "HR";
        int parentRoleId = 99;
        String parentRoleCode = "developer";

        DepartmentEntity department = new DepartmentEntity();
        department.setId(departmentId);
        department.setCode(departmentCode);

        RoleEntity parentRole = new RoleEntity();
        parentRole.setId(parentRoleId);
        parentRole.setCode(parentRoleCode);
        parentRole.setPermissions(new HashSet<>());
        RoleEntity role = new RoleEntity();

        role.setId(roleId);
        role.setCode(roleCode);
        String roleDescription = "Team Lead";
        role.setDescription(roleDescription);
        role.setStatus(Status.INACTIVE);
        role.setDepartment(department);
        role.setParent(parentRole);
        role.setPermissions(new HashSet<>());

        // When
        RoleDTO result = underTest.mapToDto(role);

        // Then
        assertNotNull(result);
        assertEquals(roleId, result.getId());
        assertEquals(roleCode, result.getCode());
        assertEquals(roleDescription, result.getDescription());
        assertEquals(Status.INACTIVE, result.getStatus());
        assertEquals(departmentCode, result.getDepartment().getCode());
        assertEquals(parentRoleCode, result.getParent().getCode());
    }


    @Test
    void itShouldMapDtoToEntity_WithoutParentRole() {
        // Given
        int departmentId = 10;
        String departmentCode = "HR";
        String roleCode = "admin";
        DepartmentEntity department = new DepartmentEntity();
        department.setId(departmentId);
        department.setCode(departmentCode);

        RoleDTO dto = new RoleDTO();
        dto.setCode(roleCode);
        String roleDescription = "Developer";
        dto.setDescription(roleDescription);
        dto.setStatus(Status.ACTIVE);
        dto.setDepartment(new CodeDescriptionDTO(department.getCode(), ""));
        dto.setParent(null);

        // When
        RoleEntity result = underTest.mapToEntity(dto, department, null);

        // Then
        assertNotNull(result);
        assertEquals(roleCode, result.getCode());
        assertEquals(roleDescription, result.getDescription());
        assertEquals(Status.ACTIVE, result.getStatus());
        assertEquals(department, result.getDepartment());
        assertNull(result.getParent(), "Expected parent to be null");
    }

    @Test
    void itShouldSetStatusToActive_WhenNotProvided() {
        // Given
        String roleCode = "admin";
        int departmentId = 10;
        String departmentCode = "HR";
        DepartmentEntity department = new DepartmentEntity();
        department.setId(departmentId);
        department.setCode(departmentCode);

        RoleDTO dto = new RoleDTO();
        String roleDescription = "Developer";
        dto.setCode(roleCode);
        dto.setDescription(roleDescription);
        dto.setStatus(null);
        dto.setDepartment(new CodeDescriptionDTO(department.getCode(), ""));
        dto.setParent(null);

        // When
        RoleEntity result = underTest.mapToEntity(dto, department, null);

        // Then
        assertNotNull(result);
        assertEquals(roleCode, result.getCode());
        assertEquals(roleDescription, result.getDescription());
        assertEquals(Status.ACTIVE, result.getStatus());
        assertEquals(department, result.getDepartment());
        assertNull(result.getParent(), "Expected parent to be null");
    }

    @Test
    void itShouldMapDtoToEntity_WithAllFields() {
        // Given
        int departmentId = 10;
        int parentRoleId = 99;
        String roleCode = "admin";
        DepartmentEntity department = new DepartmentEntity();
        department.setId(departmentId);

        RoleEntity parentRole = new RoleEntity();
        parentRole.setId(parentRoleId);
        parentRole.setPermissions(new HashSet<>());
        String parentRoleCode = "Manager";
        parentRole.setCode(parentRoleCode);

        RoleDTO dto = new RoleDTO();
        String roleDescription = "Team Lead";
        dto.setCode(roleCode);
        dto.setDescription(roleDescription);
        dto.setStatus(Status.INACTIVE);
        dto.setDepartment(new CodeDescriptionDTO(department.getCode(), ""));
        dto.setParent(new CodeDescriptionDTO(parentRole.getName(), ""));

        // When
        RoleEntity result = underTest.mapToEntity(dto, department, parentRole);

        // Then
        assertNotNull(result);
        assertEquals(roleCode, result.getCode());
        assertEquals(roleDescription, result.getDescription());
        assertEquals(Status.INACTIVE, result.getStatus());
        assertEquals(department, result.getDepartment());
        assertEquals(parentRole, result.getParent());
    }
}