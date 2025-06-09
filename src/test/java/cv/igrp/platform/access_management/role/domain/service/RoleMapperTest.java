package cv.igrp.platform.access_management.role.domain.service;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.models.Department;
import cv.igrp.platform.access_management.shared.domain.models.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

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
        Role role = new Role();
        int roleId = 1;
        role.setId(roleId);
        String roleName = "Developer";
        role.setName(roleName);
        String roleDescription = "Responsible for writing code";
        role.setDescription(roleDescription);
        role.setStatus(Status.ACTIVE);

        Department department = new Department();
        int departmentId = 10;
        department.setId(departmentId);
        role.setDepartment(department);

        role.setParent(null);

        // When
        RoleDTO result = underTest.mapToDto(role);

        // Then
        assertNotNull(result);
        assertEquals(roleId, result.getId());
        assertEquals(roleName, result.getName());
        assertEquals(roleDescription, result.getDescription());
        assertEquals(departmentId, result.getDepartmentId());
        assertEquals(Status.ACTIVE, result.getStatus());
        assertNull(result.getParentId(), "Expected parentId to be null when role has no parent");
    }

    @Test
    void itShouldMapRoleToDto_WhenParentIsPresent() {
        // Given
        int roleId = 10;
        int parentRoleId = 1;
        int departmentId = 10;

        Role role = new Role();
        Role parentRole = new Role();
        parentRole.setId(parentRoleId);
        role.setId(roleId);
        String roleName = "Developer";
        role.setName(roleName);
        String roleDescription = "Responsible for writing code";
        role.setDescription(roleDescription);
        role.setStatus(Status.ACTIVE);

        Department department = new Department();
        department.setId(departmentId);
        role.setDepartment(department);

        role.setParent(parentRole);

        // When
        RoleDTO result = underTest.mapToDto(role);

        // Then
        assertNotNull(result);
        assertEquals(roleId, result.getId());
        assertEquals(roleName, result.getName());
        assertEquals(roleDescription, result.getDescription());
        assertEquals(departmentId, result.getDepartmentId());
        assertEquals(Status.ACTIVE, result.getStatus());
        assertNotNull(result.getParentId());
        assertEquals(parentRoleId, result.getParentId());
    }

    @Test
    void itShouldMapAllFieldsCorrectly_FromRoleToDto() {
        // Given
        int roleId = 1;
        int departmentId = 20;
        int parentRoleId = 99;

        Department department = new Department();
        department.setId(departmentId);

        Role parentRole = new Role();
        parentRole.setId(parentRoleId);
        Role role = new Role();

        role.setId(roleId);
        String roleName = "Team Lead";
        role.setName(roleName);
        String roleDescription = "Leads the development team";
        role.setDescription(roleDescription);
        role.setStatus(Status.INACTIVE);
        role.setDepartment(department);
        role.setParent(parentRole);

        // When
        RoleDTO result = underTest.mapToDto(role);

        // Then
        assertNotNull(result);
        assertEquals(roleId, result.getId());
        assertEquals(roleName, result.getName());
        assertEquals(roleDescription, result.getDescription());
        assertEquals(Status.INACTIVE, result.getStatus());
        assertEquals(departmentId, result.getDepartmentId());
        assertEquals(parentRoleId, result.getParentId());
    }


    @Test
    void itShouldMapDtoToEntity_WithoutParentRole() {
        // Given
        int departmentId = 10;
        Department department = new Department();
        department.setId(departmentId);

        RoleDTO dto = new RoleDTO();
        String roleName = "Developer";
        dto.setName(roleName);
        String roleDescription = "Responsible for coding";
        dto.setDescription(roleDescription);
        dto.setStatus(Status.ACTIVE);
        dto.setDepartmentId(department.getId());
        dto.setParentId(null);

        // When
        Role result = underTest.mapToEntity(dto, department, null);

        // Then
        assertNotNull(result);
        assertEquals(roleName, result.getName());
        assertEquals(roleDescription, result.getDescription());
        assertEquals(Status.ACTIVE, result.getStatus());
        assertEquals(department, result.getDepartment());
        assertNull(result.getParent(), "Expected parent to be null");
    }

    @Test
    void itShouldSetStatusToActive_WhenNotProvided() {
        // Given
        int departmentId = 10;
        Department department = new Department();
        department.setId(departmentId);

        RoleDTO dto = new RoleDTO();
        String roleName = "Developer";
        dto.setName(roleName);
        String roleDescription = "Responsible for coding";
        dto.setDescription(roleDescription);
        dto.setStatus(null);
        dto.setDepartmentId(department.getId());
        dto.setParentId(null);

        // When
        Role result = underTest.mapToEntity(dto, department, null);

        // Then
        assertNotNull(result);
        assertEquals(roleName, result.getName());
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
        Department department = new Department();
        department.setId(departmentId);

        Role parentRole = new Role();
        parentRole.setId(parentRoleId);
        String parentRoleName = "Manager";
        parentRole.setName(parentRoleName);

        RoleDTO dto = new RoleDTO();
        String roleName = "Team Lead";
        dto.setName(roleName);
        String roleDescription = "Leads the team";
        dto.setDescription(roleDescription);
        dto.setStatus(Status.INACTIVE);
        dto.setDepartmentId(department.getId());
        dto.setParentId(parentRole.getId());

        // When
        Role result = underTest.mapToEntity(dto, department, parentRole);

        // Then
        assertNotNull(result);
        assertEquals(roleName, result.getName());
        assertEquals(roleDescription, result.getDescription());
        assertEquals(Status.INACTIVE, result.getStatus());
        assertEquals(department, result.getDepartment());
        assertEquals(parentRole, result.getParent());
    }
}