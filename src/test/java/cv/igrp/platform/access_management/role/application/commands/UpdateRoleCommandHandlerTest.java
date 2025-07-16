package cv.igrp.platform.access_management.role.application.commands;

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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void itShouldStartContext() {
        assertNotNull(underTest);
    }

    @Test
    void itShouldThrowNotFoundException_WhenProvidedRoleId_DoesNotExist() {
        //... Given
        int roleId = 100;
        RoleDTO roleData = new RoleDTO();
        String roleName = "New RoleName";
        roleData.setName(roleName);
        UpdateRoleCommand command = new UpdateRoleCommand(roleData, roleId);

        when(roleRepository.findByIdAndStatusNot(roleId, Status.DELETED))
                .thenReturn(Optional.empty());

        //... When
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> underTest.handle(command));
        //... Then
        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getBody().getStatus());

        verify(roleRepository, never()).save(any(RoleEntity.class));
    }

    @Test
    void itShouldThrowNotFoundException_WhenProvidedRoleIdIsFound_AndProvidedDepartmentId_DoesNotExist() {
        //... Given
        int roleId = 100;
        int nonExistentDepartmentId = 100;
        RoleDTO roleData = new RoleDTO();
        String roleName = "New RoleName";
        roleData.setName(roleName);
        roleData.setDepartmentId(nonExistentDepartmentId);
        UpdateRoleCommand command = new UpdateRoleCommand(roleData, roleId);

        RoleEntity existingRole = new RoleEntity();
        existingRole.setId(roleId);
        existingRole.setStatus(Status.ACTIVE);

        when(roleRepository.findByIdAndStatusNot(roleId, Status.DELETED))
                .thenReturn(Optional.of(existingRole));
        when(departmentRepository.findById(nonExistentDepartmentId))
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
        int roleIdToUpdate = 100;
        int savedRoleId = 100;
        int departmentId = 100;
        RoleDTO roleData = new RoleDTO();
        String roleName = "New RoleName";
        roleData.setName(roleName);
        roleData.setDepartmentId(departmentId);
        UpdateRoleCommand command = new UpdateRoleCommand(roleData, roleIdToUpdate);

        RoleEntity existingRole = new RoleEntity();
        existingRole.setId(roleIdToUpdate);
        existingRole.setStatus(Status.ACTIVE);

        DepartmentEntity foundDepartment = new DepartmentEntity();
        foundDepartment.setId(departmentId);
        ArrayList<RoleEntity> persistedRoles = new ArrayList<>();
        RoleEntity savedRole = new RoleEntity();
        savedRole.setId(savedRoleId);
        savedRole.setName(roleName);
        persistedRoles.add(savedRole);
        foundDepartment.setRoles(persistedRoles);

        when(roleRepository.findByIdAndStatusNot(roleIdToUpdate, Status.DELETED))
                .thenReturn(Optional.of(existingRole));
        when(departmentRepository.findById(departmentId))
                .thenReturn(Optional.of(foundDepartment));

        //... When
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> underTest.handle(command));
        //... Then
        assertEquals(HttpStatus.CONFLICT.value(), ex.getBody().getStatus());
        verify(roleRepository, never()).save(any(RoleEntity.class));
    }

    @Test
    void itShouldThrowNotFoundException_WhenProvidedParentRoleDoesNotExist() {
        //... Given
        int roleId = 110;
        int existentDepartmentId = 100;
        int nonExistentParentRoleId = 100;
        RoleDTO roleData = new RoleDTO();
        String roleName = "New RoleName";
        roleData.setName(roleName);
        roleData.setParentId(nonExistentParentRoleId);
        roleData.setDepartmentId(existentDepartmentId);
        UpdateRoleCommand command = new UpdateRoleCommand(roleData, roleId);

        RoleEntity existingRole = new RoleEntity();
        existingRole.setId(roleId);
        existingRole.setStatus(Status.ACTIVE);

        when(roleRepository.findByIdAndStatusNot(roleId, Status.DELETED))
                .thenReturn(Optional.of(existingRole));
        DepartmentEntity department = new DepartmentEntity();
        department.setId(existentDepartmentId);
        department.setStatus(DepartmentStatus.ACTIVE);

        when(departmentRepository.findById(existentDepartmentId))
                .thenReturn(Optional.of(department));

        //... When
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> underTest.handle(command));
        //... Then
        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getBody().getStatus());
        verify(roleRepository, never()).save(any(RoleEntity.class));
    }

    @Test
    void itShouldUpdateOnlyChangedFields() {
        // Given
        int roleId = 1;

        DepartmentEntity department = new DepartmentEntity();
        int departmentId = 10;
        department.setId(departmentId);
        department.setName("Dept");

        RoleEntity existingRole = new RoleEntity();
        existingRole.setId(roleId);
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
        updatedData.setDepartmentId(departmentId);
        updatedData.setParentId(null);

        UpdateRoleCommand command = new UpdateRoleCommand(updatedData, roleId);

        RoleEntity updatedRole = new RoleEntity();
        updatedRole.setId(roleId);
        updatedRole.setName("New Name");
        updatedRole.setDescription("New Desc");
        updatedRole.setStatus(Status.ACTIVE);
        updatedRole.setDepartment(department);

        RoleDTO responseDto = new RoleDTO();
        responseDto.setId(roleId);
        responseDto.setName(roleNewName);

        when(roleRepository.findByIdAndStatusNot(roleId, Status.DELETED)).thenReturn(Optional.of(existingRole));
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(department));
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
        int roleId = 1;

        DepartmentEntity department = new DepartmentEntity();
        int departmentId = 10;
        department.setId(departmentId);
        department.setName("Dept");

        RoleEntity existingRole = new RoleEntity();
        existingRole.setId(roleId);
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
        updatedData.setDepartmentId(departmentId);
        updatedData.setParentId(null);

        UpdateRoleCommand command = new UpdateRoleCommand(updatedData, roleId);

        RoleEntity updatedRole = new RoleEntity();
        updatedRole.setId(roleId);
        updatedRole.setName("New Name");
        updatedRole.setDescription("New Desc");
        updatedRole.setStatus(Status.ACTIVE);
        updatedRole.setDepartment(department);

        RoleDTO responseDto = new RoleDTO();
        responseDto.setId(roleId);
        responseDto.setName(roleNewName);

        when(roleRepository.findByIdAndStatusNot(roleId, Status.DELETED)).thenReturn(Optional.of(existingRole));
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(department));
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