package cv.igrp.platform.access_management.role.application.commands;

import cv.igrp.framework.auth.core.adapter.IAdapter;
import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.CodeDescriptionDTO;
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
        String roleCode = "app";
        RoleDTO roleData = new RoleDTO();
        String roleDesc = "New RoleName";
        roleData.setCode(roleCode);
        roleData.setDescription(roleDesc);
        UpdateRoleCommand command = new UpdateRoleCommand(roleData, roleCode);

        when(roleRepository.findByCodeAndStatusNot(roleCode, Status.DELETED))
                .thenReturn(Optional.empty());

        //... When
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> underTest.handle(command));
        //... Then
        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getBody().getStatus());

        verify(roleRepository, never()).save(any(RoleEntity.class));
    }

    @Test
    void itShouldThrowNotFoundException_WhenProvidedParentRoleDoesNotExist() {
        // Given
        String roleCode = "app";
        String existentDepartmentCode = "app";
        String nonExistentParentRoleCode = "parent-does-not-exist";

        RoleDTO roleData = new RoleDTO();
        roleData.setCode(roleCode);
        roleData.setParent(new CodeDescriptionDTO(nonExistentParentRoleCode, ""));
        roleData.setDepartment(new CodeDescriptionDTO(existentDepartmentCode, ""));
        UpdateRoleCommand command = new UpdateRoleCommand(roleData, roleCode);

        RoleEntity existingRole = new RoleEntity();
        existingRole.setCode(roleCode);
        existingRole.setStatus(Status.ACTIVE);

        // Stub main role exists
        when(roleRepository.findByCodeAndStatusNot(roleCode, Status.DELETED))
                .thenReturn(Optional.of(existingRole));

        // Stub parent role does NOT exist
        when(roleRepository.findByCodeAndStatusNot(nonExistentParentRoleCode, Status.DELETED))
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
        assertTrue(ex.getBody().getProperties().getOrDefault("details", "").toString().contains(("Parent Role with code: " + nonExistentParentRoleCode)));
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
        String rolePreviousCode = "Role Previous Name";
        existingRole.setCode(rolePreviousCode);
        String rolePreviousDescription = "Role Previous Description";
        existingRole.setDescription(rolePreviousDescription);
        existingRole.setStatus(Status.ACTIVE);
        existingRole.setDepartment(department);
        existingRole.setParent(null);

        RoleDTO updatedData = new RoleDTO();
        String roleNewCode = "Role New Name";
        updatedData.setCode(roleNewCode);
        String roleNewDescription = "Role New Description";
        updatedData.setDescription(roleNewDescription);
        updatedData.setStatus(Status.ACTIVE);
        updatedData.setDepartment(new CodeDescriptionDTO(departmentCode, ""));
        updatedData.setParent(null);

        UpdateRoleCommand command = new UpdateRoleCommand(updatedData, rolePreviousCode);

        RoleEntity updatedRole = new RoleEntity();
        updatedRole.setCode("New Name");
        updatedRole.setDescription("New Desc");
        updatedRole.setStatus(Status.ACTIVE);
        updatedRole.setDepartment(department);

        RoleDTO responseDto = new RoleDTO();
        responseDto.setCode(roleNewCode);

        when(roleRepository.findByCodeAndStatusNot(rolePreviousCode, Status.DELETED)).thenReturn(Optional.of(existingRole));
        when(roleRepository.save(existingRole)).thenReturn(updatedRole);
        when(roleMapper.mapToDto(updatedRole)).thenReturn(responseDto);

        // When
        ResponseEntity<RoleDTO> result = underTest.handle(command);

        // Then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        Assertions.assertNotNull(result.getBody());
        assertEquals(roleNewCode, result.getBody().getCode());
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
        String rolePreviousCode = "Role Previous Name";
        existingRole.setCode(rolePreviousCode);
        String rolePreviousDescription = "Role Previous Description";
        existingRole.setDescription(rolePreviousDescription);
        existingRole.setStatus(Status.ACTIVE);
        existingRole.setDepartment(department);
        existingRole.setParent(null);

        RoleDTO updatedData = new RoleDTO();
        String roleNewCode = "Role New Code";
        updatedData.setCode(roleNewCode);
        String roleNewDescription = "Role New Description";
        updatedData.setDescription(roleNewDescription);
        updatedData.setStatus(Status.ACTIVE);
        updatedData.setDepartment(new CodeDescriptionDTO(departmentCode, ""));
        updatedData.setParent(null);

        UpdateRoleCommand command = new UpdateRoleCommand(updatedData, rolePreviousCode);

        RoleEntity updatedRole = new RoleEntity();
        updatedRole.setCode("New Name");
        updatedRole.setDescription("New Desc");
        updatedRole.setStatus(Status.ACTIVE);
        updatedRole.setDepartment(department);

        RoleDTO responseDto = new RoleDTO();
        responseDto.setCode(roleNewCode);

        when(roleRepository.findByCodeAndStatusNot(rolePreviousCode, Status.DELETED)).thenReturn(Optional.of(existingRole));
        when(roleRepository.save(existingRole)).thenReturn(updatedRole);
        when(roleMapper.mapToDto(updatedRole)).thenReturn(responseDto);

        // When
        ResponseEntity<RoleDTO> result = underTest.handle(command);

        // Then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        Assertions.assertNotNull(result.getBody());
        assertEquals(roleNewCode, result.getBody().getCode());

        verify(roleRepository).save(existingRole);
        verify(roleMapper).mapToDto(updatedRole);
    }
}
