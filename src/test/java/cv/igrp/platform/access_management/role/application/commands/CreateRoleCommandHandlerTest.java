package cv.igrp.platform.access_management.role.application.commands;

import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CreateRoleCommandHandlerTest {

    @InjectMocks
    private CreateRoleCommandHandler underTest;
    @Mock
    private DepartmentEntityRepository departmentRepository;
    @Mock
    private RoleEntityRepository roleRepository;
    @Mock
    private RoleMapper roleMapper;

    @Test
    void itShouldStartContext() {
        assertThat(underTest).isNotNull();
    }

    @Test
    void itShouldThrowRecordNotFoundException_When_CreatingARole_AndDepartment_NotFound() {
        //... Given
        RoleDTO role = new RoleDTO();
        String departmentCode = "RH";
        String roleName = "Role Name";
        role.setDepartmentCode(departmentCode);
        role.setName(roleName);
        String roleDescription = "Role Description";
        role.setDescription(roleDescription);
        CreateRoleCommand command = new CreateRoleCommand(role);

        //... When
        when(departmentRepository.findByCode(departmentCode)).thenReturn(Optional.empty());
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> underTest.handle(command));
        //... Then
        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getBody().getStatus());
    }

    @Test
    void itShouldThrowRecordNotFoundException_When_CreatingARole_AndRoleParentName_NotFound() {
        //... Given
        RoleDTO role = new RoleDTO();
        String departmentCode = "RH";
        String roleParentName = "admin";
        String roleName = "Role Name";
        role.setDepartmentCode(departmentCode);
        role.setName(roleName);
        role.setParentName(roleParentName);
        String roleDescription = "Role Description";
        role.setDescription(roleDescription);
        CreateRoleCommand command = new CreateRoleCommand(role);
        DepartmentEntity department = new DepartmentEntity();
        department.setCode(departmentCode);
        String departmentName = "Department Name";
        department.setName(departmentName);

        //... When
        when(departmentRepository.findByCode(departmentCode)).thenReturn(Optional.of(department));
        when(roleRepository.findByNameAndStatusNot(roleParentName, Status.DELETED))
                .thenReturn(Optional.empty());
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> underTest.handle(command));
        //... Then
        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getBody().getStatus());
    }

    @Test
    void itShouldHandle_WhenDepartment_HasNoRolesAssociated() {
        // Given
        RoleDTO role = new RoleDTO();
        String departmentCode = "RH";
        String roleName = "Role Name";
        String roleDescription = "Role Description";

        role.setDepartmentCode(departmentCode);
        role.setName(roleName);
        role.setDescription(roleDescription);
        role.setParentName(null);

        CreateRoleCommand command = new CreateRoleCommand(role);

        DepartmentEntity department = new DepartmentEntity();
        department.setCode(departmentCode);
        department.setName("Department Name");

        RoleEntity roleEntity = new RoleEntity();
        RoleEntity savedRole = new RoleEntity();
        RoleDTO expectedResponse = new RoleDTO();

        // When
        when(departmentRepository.findByCode(departmentCode)).thenReturn(Optional.of(department));
        when(roleMapper.mapToEntity(role, department, null)).thenReturn(roleEntity);
        when(roleRepository.save(roleEntity)).thenReturn(savedRole);
        when(roleMapper.mapToDto(savedRole)).thenReturn(expectedResponse);

        ResponseEntity<RoleDTO> response = underTest.handle(command);

        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void itShouldThrowBadRequestException_WhenNewRoleName_Exists_InThe_SameDepartment() {
        // Given
        RoleDTO role = new RoleDTO();
        String departmentCode = "RH";
        String roleName = "create_resource";
        String roleDescription = "Role Description";

        role.setDepartmentCode(departmentCode);
        role.setName(roleName.toUpperCase());
        role.setDescription(roleDescription);
        role.setParentName(null);

        CreateRoleCommand command = new CreateRoleCommand(role);

        DepartmentEntity department = new DepartmentEntity();
        department.setCode(departmentCode);
        department.setName("Department Name");
        ArrayList<RoleEntity> persistedRoles = new ArrayList<>();
        RoleEntity savedRole = new RoleEntity();
        savedRole.setName(roleName);
        persistedRoles.add(savedRole);
        department.setRoles(persistedRoles);

        when(departmentRepository.findByCode(departmentCode)).thenReturn(Optional.of(department));

        // When

        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> underTest.handle(command));

        //... Then
        assertEquals(HttpStatus.CONFLICT.value(), ex.getBody().getStatus());
        verify(roleRepository, never()).findByNameAndStatusNot(any(), any());
        verify(roleRepository, never()).save(any());
    }

    @Test
    void itShouldCreateRole_WhenDepartment_HasRolesAssociated() {
        // Given
        RoleDTO role = new RoleDTO();
        String departmentCode = "RH";
        String roleName = "update_resource";
        String savedRoleName = "create_resource";
        String roleDescription = "Role Description";

        role.setDepartmentCode(departmentCode);
        role.setName(roleName);
        role.setDescription(roleDescription);
        role.setParentName(null);

        CreateRoleCommand command = new CreateRoleCommand(role);

        DepartmentEntity department = new DepartmentEntity();
        department.setCode(departmentCode);
        department.setName("Department Name");

        RoleEntity roleEntity = new RoleEntity();
        RoleEntity savedRole = new RoleEntity();
        savedRole.setName(savedRoleName);
        RoleDTO expectedResponse = new RoleDTO();

        // When
        when(departmentRepository.findByCode(departmentCode)).thenReturn(Optional.of(department));
        when(roleMapper.mapToEntity(role, department, null)).thenReturn(roleEntity);
        when(roleRepository.save(roleEntity)).thenReturn(savedRole);
        when(roleMapper.mapToDto(savedRole)).thenReturn(expectedResponse);

        ResponseEntity<RoleDTO> response = underTest.handle(command);

        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void itShouldCreateNewRole_When_ParentRoleName_IsNotProvided() {
        // Given
        RoleDTO role = new RoleDTO();
        String departmentCode = "RH";
        String roleName = "Role Name";
        String roleDescription = "Role Description";

        role.setDepartmentCode(departmentCode);
        role.setName(roleName);
        role.setDescription(roleDescription);
        role.setParentName(null);

        CreateRoleCommand command = new CreateRoleCommand(role);

        DepartmentEntity department = new DepartmentEntity();
        department.setCode(departmentCode);
        department.setName("Department Name");

        RoleEntity roleEntity = new RoleEntity();
        RoleEntity savedRole = new RoleEntity();
        RoleDTO expectedResponse = new RoleDTO();

        // When
        when(departmentRepository.findByCode(departmentCode)).thenReturn(Optional.of(department));
        when(roleMapper.mapToEntity(role, department, null)).thenReturn(roleEntity);
        when(roleRepository.save(roleEntity)).thenReturn(savedRole);
        when(roleMapper.mapToDto(savedRole)).thenReturn(expectedResponse);

        ResponseEntity<RoleDTO> response = underTest.handle(command);

        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(expectedResponse, response.getBody());

        verify(roleRepository, never()).findByNameAndStatusNot(any(), any());
    }

    @Test
    void itShouldCreateNewRole_WithParentRoleName_When_ParentRoleName_IsProvided() {
        // Given
        RoleDTO role = new RoleDTO();
        String departmentCode = "RH";
        String parentRoleName = "RH";
        String roleName = "Role Name";
        String roleDescription = "Role Description";

        role.setDepartmentCode(departmentCode);
        role.setName(roleName);
        role.setDescription(roleDescription);
        role.setParentName(parentRoleName);

        CreateRoleCommand command = new CreateRoleCommand(role);

        DepartmentEntity department = new DepartmentEntity();
        department.setCode(departmentCode);
        department.setName("Department Name");

        RoleEntity roleEntity = new RoleEntity();
        RoleEntity parentRole = new RoleEntity();
        parentRole.setName(parentRoleName);
        parentRole.setName("Parent Role Name");
        RoleEntity savedRole = new RoleEntity();
        RoleDTO expectedResponse = new RoleDTO();
        expectedResponse.setParentName(parentRoleName);
        expectedResponse.setName(roleName);
        expectedResponse.setDescription(roleDescription);

        // When
        when(departmentRepository.findByCode(departmentCode)).thenReturn(Optional.of(department));
        when(roleRepository.findByNameAndStatusNot(parentRoleName, Status.DELETED))
                .thenReturn(Optional.of(parentRole));
        when(roleMapper.mapToEntity(role, department, parentRole)).thenReturn(roleEntity);
        when(roleRepository.save(roleEntity)).thenReturn(savedRole);
        when(roleMapper.mapToDto(savedRole)).thenReturn(expectedResponse);

        ResponseEntity<RoleDTO> response = underTest.handle(command);

        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(expectedResponse, response.getBody());
    }
}
