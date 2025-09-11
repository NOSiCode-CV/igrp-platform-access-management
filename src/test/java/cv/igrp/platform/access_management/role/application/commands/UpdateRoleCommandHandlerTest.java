package cv.igrp.platform.access_management.role.application.commands;

import cv.igrp.framework.auth.core.adapter.IAdapter;
import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.Optional;

import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UpdateRoleCommandHandlerTest {

    @InjectMocks
    private UpdateRoleCommandHandler underTest;
    @Mock
    private RoleEntityRepository roleRepository;
    @Mock
    private DepartmentEntityRepository departmentRepository;
    @Mock
    private RoleMapper roleMapper;
    @Mock
    private IAdapter adapter;

    @Test
    void itShouldStartContext() {
        assertNotNull(underTest);
    }

    @Test
    void itShouldThrowNotFoundException_WhenProvidedRoleName_DoesNotExist() {
        //... Given
        String roleName = "app";
        RoleDTO roleData = new RoleDTO();
        String roleDesc = "New RoleName";
        roleData.setName(roleName);
        roleData.setDescription(roleDesc);
        UpdateRoleCommand command = new UpdateRoleCommand(roleData, roleName);

        when(roleRepository.findByNameAndStatusNot(roleName, Status.DELETED))
                .thenReturn(Optional.empty());

        //... When
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> underTest.handle(command));
        //... Then
        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getBody().getStatus());

        verify(roleRepository, never()).save(any(RoleEntity.class));
    }

    @Test
    void itShouldThrowNotBadException_RoleName_Exists_InProvidedDepartment() {
        //... Given
        String roleNameToUpdate = "app";
        String alreadyPresentRoleName = "admin";
        String departmentCode = "app";
        DepartmentEntity foundDepartment = new DepartmentEntity();
        foundDepartment.setCode(departmentCode);
        RoleDTO roleData = new RoleDTO();
        roleData.setName(alreadyPresentRoleName);
        roleData.setDepartmentCode(departmentCode);
        UpdateRoleCommand command = new UpdateRoleCommand(roleData, roleNameToUpdate);

        RoleEntity existingRole = new RoleEntity();
        existingRole.setName(roleNameToUpdate);
        existingRole.setStatus(Status.ACTIVE);
        existingRole.setDepartment(foundDepartment);

        RoleEntity anotherExistingRole = new RoleEntity();
        anotherExistingRole.setName(alreadyPresentRoleName);
        anotherExistingRole.setStatus(Status.ACTIVE);
        anotherExistingRole.setDepartment(foundDepartment);

        ArrayList<RoleEntity> persistedRoles = new ArrayList<>();
        RoleEntity savedRole = new RoleEntity();
        savedRole.setName(alreadyPresentRoleName);
        savedRole.setStatus(Status.ACTIVE);
        savedRole.setDepartment(foundDepartment);
        persistedRoles.add(savedRole);
        persistedRoles.add(anotherExistingRole);
        foundDepartment.setRoles(persistedRoles);

        when(roleRepository.findByNameAndStatusNot(roleNameToUpdate, Status.DELETED))
                .thenReturn(Optional.of(existingRole));

        //... When
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> underTest.handle(command));
        //... Then
        assertEquals(HttpStatus.CONFLICT.value(), ex.getBody().getStatus());
        verify(roleRepository, never()).save(any(RoleEntity.class));
    }

    @Test
    void itShouldThrowNotFoundException_WhenProvidedParentRoleDoesNotExist() {
        // Given
        String roleName = "app";
        String existentDepartmentCode = "app";
        String nonExistentParentRoleName = "parent-does-not-exist";

        RoleDTO roleData = new RoleDTO();
        roleData.setName(roleName);
        roleData.setParentName(nonExistentParentRoleName);
        roleData.setDepartmentCode(existentDepartmentCode);
        UpdateRoleCommand command = new UpdateRoleCommand(roleData, roleName);

        RoleEntity existingRole = new RoleEntity();
        existingRole.setName(roleName);
        existingRole.setStatus(Status.ACTIVE);

        // Stub main role exists
        when(roleRepository.findByNameAndStatusNot(roleName, Status.DELETED))
                .thenReturn(Optional.of(existingRole));

        // Stub parent role does NOT exist
        when(roleRepository.findByNameAndStatusNot(nonExistentParentRoleName, Status.DELETED))
                .thenReturn(Optional.empty());

        // When
        IgrpResponseStatusException ex = assertThrows(
                IgrpResponseStatusException.class,
                () -> underTest.handle(command)
        );

        // Then
        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getBody().getStatus());
        System.out.println(ex.getBody());
        Assertions.assertNotNull(ex.getBody().getProperties());
        assertTrue(ex.getBody().getProperties().getOrDefault("details", "").toString().contains(("Parent Role with name: " + nonExistentParentRoleName)));
        verify(roleRepository, never()).save(any(RoleEntity.class));
    }

    @Test
    void itShouldUpdateOnlyChangedFields() {
        // Given

        DepartmentEntity department = new DepartmentEntity();
        String departmentCode = "app";
        department.setCode(departmentCode);
        department.setName("Dept");

        RoleEntity existingRole = new RoleEntity();
        String rolePreviousName = "Role Previous Name";
        existingRole.setName(rolePreviousName);
        String rolePreviousDescription = "Role Previous Description";
        existingRole.setDescription(rolePreviousDescription);
        existingRole.setStatus(Status.ACTIVE);
        existingRole.setDepartment(department);
        existingRole.setParent(null);

        RoleDTO updatedData = new RoleDTO();
        String roleNewName = "Role New Name";
        updatedData.setName(roleNewName);
        String roleNewDescription = "Role New Description";
        updatedData.setDescription(roleNewDescription);
        updatedData.setStatus(Status.ACTIVE);
        updatedData.setDepartmentCode(departmentCode);
        updatedData.setParentName(null);

        UpdateRoleCommand command = new UpdateRoleCommand(updatedData, rolePreviousName);

        RoleEntity updatedRole = new RoleEntity();
        updatedRole.setName("New Name");
        updatedRole.setDescription("New Desc");
        updatedRole.setStatus(Status.ACTIVE);
        updatedRole.setDepartment(department);

        RoleDTO responseDto = new RoleDTO();
        responseDto.setName(roleNewName);

        when(roleRepository.findByNameAndStatusNot(rolePreviousName, Status.DELETED)).thenReturn(Optional.of(existingRole));
        when(roleRepository.save(existingRole)).thenReturn(updatedRole);
        when(roleMapper.mapToDto(updatedRole)).thenReturn(responseDto);

        // When
        ResponseEntity<RoleDTO> result = underTest.handle(command);

        // Then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        Assertions.assertNotNull(result.getBody());
        assertEquals(roleNewName, result.getBody().getName());
        assertEquals(roleNewDescription, existingRole.getDescription());
        assertEquals(Status.ACTIVE, existingRole.getStatus());

        verify(roleRepository).save(existingRole);
        verify(roleMapper).mapToDto(updatedRole);
    }

    @Test
    void itShouldPersistUpdatedRole() {
        // Given

        DepartmentEntity department = new DepartmentEntity();
        String departmentCode = "app";
        department.setCode(departmentCode);
        department.setName("Dept");

        RoleEntity existingRole = new RoleEntity();
        String rolePreviousName = "Role Previous Name";
        existingRole.setName(rolePreviousName);
        String rolePreviousDescription = "Role Previous Description";
        existingRole.setDescription(rolePreviousDescription);
        existingRole.setStatus(Status.ACTIVE);
        existingRole.setDepartment(department);
        existingRole.setParent(null);

        RoleDTO updatedData = new RoleDTO();
        String roleNewName = "Role New Name";
        updatedData.setName(roleNewName);
        String roleNewDescription = "Role New Description";
        updatedData.setDescription(roleNewDescription);
        updatedData.setStatus(Status.ACTIVE);
        updatedData.setDepartmentCode(departmentCode);
        updatedData.setParentName(null);

        UpdateRoleCommand command = new UpdateRoleCommand(updatedData, rolePreviousName);

        RoleEntity updatedRole = new RoleEntity();
        updatedRole.setName("New Name");
        updatedRole.setDescription("New Desc");
        updatedRole.setStatus(Status.ACTIVE);
        updatedRole.setDepartment(department);

        RoleDTO responseDto = new RoleDTO();
        responseDto.setName(roleNewName);

        when(roleRepository.findByNameAndStatusNot(rolePreviousName, Status.DELETED)).thenReturn(Optional.of(existingRole));
        when(roleRepository.save(existingRole)).thenReturn(updatedRole);
        when(roleMapper.mapToDto(updatedRole)).thenReturn(responseDto);

        // When
        ResponseEntity<RoleDTO> result = underTest.handle(command);

        // Then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        Assertions.assertNotNull(result.getBody());
        assertEquals(roleNewName, result.getBody().getName());

        verify(roleRepository).save(existingRole);
        verify(roleMapper).mapToDto(updatedRole);
    }
}
