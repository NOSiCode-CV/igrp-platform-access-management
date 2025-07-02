package cv.igrp.platform.access_management.role.application.commands.handlers;

import cv.igrp.platform.access_management.role.application.commands.commands.CreateRoleCommand;
import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Department;
import cv.igrp.platform.access_management.shared.domain.models.Role;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.DepartmentRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.RoleRepository;
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
    private DepartmentRepository departmentRepository;
    @Mock
    private RoleRepository roleRepository;
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
        int departmentId = 1;
        String roleName = "Role Name";
        role.setDepartmentId(departmentId);
        role.setName(roleName);
        String roleDescription = "Role Description";
        role.setDescription(roleDescription);
        CreateRoleCommand command = new CreateRoleCommand(role);

        //... When
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.empty());
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> underTest.handle(command));
        //... Then
        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getBody().getStatus());
    }

    @Test
    void itShouldThrowRecordNotFoundException_When_CreatingARole_AndRoleParentId_NotFound() {
        //... Given
        RoleDTO role = new RoleDTO();
        int departmentId = 1;
        int roleParentId = 1;
        String roleName = "Role Name";
        role.setDepartmentId(departmentId);
        role.setName(roleName);
        role.setParentId(roleParentId);
        String roleDescription = "Role Description";
        role.setDescription(roleDescription);
        CreateRoleCommand command = new CreateRoleCommand(role);
        Department department = new Department();
        department.setId(departmentId);
        String departmentName = "Department Name";
        department.setName(departmentName);

        //... When
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(department));
        when(roleRepository.findByIdAndStatusNot(roleParentId, Status.DELETED))
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
        int departmentId = 1;
        String roleName = "Role Name";
        String roleDescription = "Role Description";

        role.setDepartmentId(departmentId);
        role.setName(roleName);
        role.setDescription(roleDescription);
        role.setParentId(null);

        CreateRoleCommand command = new CreateRoleCommand(role);

        Department department = new Department();
        department.setId(departmentId);
        department.setName("Department Name");

        Role roleEntity = new Role();
        Role savedRole = new Role();
        RoleDTO expectedResponse = new RoleDTO();

        // When
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(department));
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
        int departmentId = 1;
        String roleName = "create_resource";
        String roleDescription = "Role Description";

        role.setDepartmentId(departmentId);
        role.setName(roleName.toUpperCase());
        role.setDescription(roleDescription);
        role.setParentId(null);

        CreateRoleCommand command = new CreateRoleCommand(role);

        Department department = new Department();
        department.setId(departmentId);
        department.setName("Department Name");
        ArrayList<Role> persistedRoles = new ArrayList<>();
        Role savedRole = new Role();
        savedRole.setName(roleName);
        persistedRoles.add(savedRole);
        department.setRoles(persistedRoles);

        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(department));

        // When

        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> underTest.handle(command));

        //... Then
        assertEquals(HttpStatus.BAD_REQUEST.value(), ex.getBody().getStatus());
        verify(roleRepository, never()).findByIdAndStatusNot(any(), any());
        verify(roleRepository, never()).save(any());
    }

    @Test
    void itShouldCreateRole_WhenDepartment_HasRolesAssociated() {
        // Given
        RoleDTO role = new RoleDTO();
        int departmentId = 1;
        String roleName = "update_resource";
        String savedRoleName = "create_resource";
        String roleDescription = "Role Description";

        role.setDepartmentId(departmentId);
        role.setName(roleName);
        role.setDescription(roleDescription);
        role.setParentId(null);

        CreateRoleCommand command = new CreateRoleCommand(role);

        Department department = new Department();
        department.setId(departmentId);
        department.setName("Department Name");

        Role roleEntity = new Role();
        Role savedRole = new Role();
        savedRole.setName(savedRoleName);
        RoleDTO expectedResponse = new RoleDTO();

        // When
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(department));
        when(roleMapper.mapToEntity(role, department, null)).thenReturn(roleEntity);
        when(roleRepository.save(roleEntity)).thenReturn(savedRole);
        when(roleMapper.mapToDto(savedRole)).thenReturn(expectedResponse);

        ResponseEntity<RoleDTO> response = underTest.handle(command);

        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void itShouldCreateNewRole_When_ParentRoleId_IsNotProvided() {
        // Given
        RoleDTO role = new RoleDTO();
        int departmentId = 1;
        String roleName = "Role Name";
        String roleDescription = "Role Description";

        role.setDepartmentId(departmentId);
        role.setName(roleName);
        role.setDescription(roleDescription);
        role.setParentId(null);

        CreateRoleCommand command = new CreateRoleCommand(role);

        Department department = new Department();
        department.setId(departmentId);
        department.setName("Department Name");

        Role roleEntity = new Role();
        Role savedRole = new Role();
        RoleDTO expectedResponse = new RoleDTO();

        // When
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(department));
        when(roleMapper.mapToEntity(role, department, null)).thenReturn(roleEntity);
        when(roleRepository.save(roleEntity)).thenReturn(savedRole);
        when(roleMapper.mapToDto(savedRole)).thenReturn(expectedResponse);

        ResponseEntity<RoleDTO> response = underTest.handle(command);

        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(expectedResponse, response.getBody());

        verify(roleRepository, never()).findByIdAndStatusNot(any(), any());
    }

    @Test
    void itShouldCreateNewRole_WithParentRoleId_When_ParentRoleId_IsProvided() {
        // Given
        RoleDTO role = new RoleDTO();
        int departmentId = 1;
        Integer parentRoleId = 1;
        String roleName = "Role Name";
        String roleDescription = "Role Description";

        role.setDepartmentId(departmentId);
        role.setName(roleName);
        role.setDescription(roleDescription);
        role.setParentId(parentRoleId);

        CreateRoleCommand command = new CreateRoleCommand(role);

        Department department = new Department();
        department.setId(departmentId);
        department.setName("Department Name");

        Role roleEntity = new Role();
        Role parentRole = new Role();
        parentRole.setId(parentRoleId);
        parentRole.setName("Parent Role Name");
        Role savedRole = new Role();
        RoleDTO expectedResponse = new RoleDTO();
        expectedResponse.setParentId(parentRoleId);
        expectedResponse.setName(roleName);
        expectedResponse.setDescription(roleDescription);

        // When
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(department));
        when(roleRepository.findByIdAndStatusNot(parentRoleId, Status.DELETED))
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