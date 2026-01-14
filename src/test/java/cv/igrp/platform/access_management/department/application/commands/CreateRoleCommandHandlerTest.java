package cv.igrp.platform.access_management.department.application.commands;

import cv.igrp.framework.auth.core.adapter.IAdapter;
import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.role.domain.service.RoleValidator;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.validation.ResourceValidationResponse;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
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
    @Mock
    @SuppressWarnings("unused")
    private IAdapter iAdapter;

    @Test
    void itShouldStartContext() {
        assertThat(underTest).isNotNull();
    }

    @Test
    void itShouldThrowRecordNotFoundException_When_CreatingARole_AndDepartment_NotFound() {
        //... Given
        RoleDTO role = new RoleDTO();
        String departmentCode = "RH";
        String roleCode = "Role Name";
        role.setDepartmentCode(departmentCode);
        role.setCode(roleCode);
        String roleDescription = "Role Description";
        role.setDescription(roleDescription);
        CreateRoleCommand command = new CreateRoleCommand(role, "RH");

        //... When
        when(departmentRepository.findByCodeAndStatusNot(departmentCode, DepartmentStatus.DELETED)).thenReturn(Optional.empty());
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
        String roleParentCode = "admin";
        String roleCode = "Role Name";
        role.setDepartmentCode(departmentCode);
        role.setCode(roleCode);
        role.setParentCode(roleParentCode);
        String roleDescription = "Role Description";
        role.setDescription(roleDescription);
        CreateRoleCommand command = new CreateRoleCommand(role, departmentCode);
        DepartmentEntity department = new DepartmentEntity();
        department.setCode(departmentCode);
        String departmentName = "Department Name";
        department.setName(departmentName);

        //... When
        when(departmentRepository.findByCodeAndStatusNot(departmentCode, DepartmentStatus.DELETED)).thenReturn(Optional.of(department));
        when(roleRepository.findByDepartmentAndCodeAndStatusNot(department, roleParentCode, Status.DELETED))
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
        String roleCode = "Role Name";
        String roleDescription = "Role Description";

        role.setDepartmentCode(departmentCode);
        role.setCode(roleCode);
        role.setDescription(roleDescription);
        role.setParentCode(null);

        CreateRoleCommand command = new CreateRoleCommand(role, departmentCode);

        DepartmentEntity department = new DepartmentEntity();
        department.setCode(departmentCode);
        department.setName("Department Name");

        RoleEntity roleEntity = new RoleEntity();
        roleEntity.setCode(roleCode);
        RoleEntity savedRole = new RoleEntity();
        savedRole.setCode(roleCode);
        RoleDTO expectedResponse = new RoleDTO();

        // When
        when(departmentRepository.findByCodeAndStatusNot(departmentCode, DepartmentStatus.DELETED)).thenReturn(Optional.of(department));
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
        String departmentCode = "RH";

        DepartmentEntity department = new DepartmentEntity();
        department.setCode(departmentCode);
        department.setName("Department Name");
        department.setStatus(DepartmentStatus.ACTIVE);
        department.setRoles(new ArrayList<>());

        RoleDTO role = new RoleDTO();
        String roleCode = "create_resource";
        role.setDepartmentCode(departmentCode);
        role.setCode(roleCode);
        role.setDescription("Role Description");
        role.setStatus(Status.ACTIVE);
        role.setParentCode(null);

        CreateRoleCommand command = new CreateRoleCommand(role, departmentCode);

        RoleEntity existingRole = new RoleEntity();
        existingRole.setId(1);
        existingRole.setCode(roleCode);
        existingRole.setStatus(Status.ACTIVE);
        existingRole.setParent(null);
        department.getRoles().add(existingRole);

        RoleEntity savedRole = new RoleEntity();
        savedRole.setCode(roleCode);
        savedRole.setStatus(Status.ACTIVE);

        ResourceValidationResponse invalidResponse = new ResourceValidationResponse();
        invalidResponse.setValid(false);
        invalidResponse.setFailureMessage(List.of("Role already exists in department RH"));

        when(departmentRepository.findByCodeAndStatusNot(departmentCode, DepartmentStatus.DELETED))
                .thenReturn(Optional.of(department));

        // Mock static RoleValidator during the service call
        try (MockedStatic<RoleValidator> mocked = mockStatic(RoleValidator.class)) {
            mocked.when(() -> RoleValidator.validateRoleDto(role, department))
                    .thenReturn(invalidResponse);

            // When
            IgrpResponseStatusException ex = assertThrows(
                    IgrpResponseStatusException.class,
                    () -> underTest.handle(command)
            );

            // Then
            assertEquals(HttpStatus.CONFLICT.value(), ex.getBody().getStatus());
            mocked.verify(() -> RoleValidator.validateRoleDto(role, department));
        }
    }

    @Test
    void itShouldCreateRole_WhenDepartment_HasRolesAssociated() {
        // Given
        RoleDTO role = new RoleDTO();
        String departmentCode = "RH";
        String roleCode = "update_resource";
        String savedRoleCode = "create_resource";
        String roleDescription = "Role Description";

        role.setDepartmentCode(departmentCode);
        role.setCode(roleCode);
        role.setDescription(roleDescription);
        role.setParentCode(null);

        CreateRoleCommand command = new CreateRoleCommand(role, departmentCode);

        DepartmentEntity department = new DepartmentEntity();
        department.setCode(departmentCode);
        department.setName("Department Name");

        RoleEntity roleEntity = new RoleEntity();
        RoleEntity savedRole = new RoleEntity();
        savedRole.setCode(savedRoleCode);
        RoleDTO expectedResponse = new RoleDTO();

        // When
        when(departmentRepository.findByCodeAndStatusNot(departmentCode, DepartmentStatus.DELETED)).thenReturn(Optional.of(department));
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
        String roleCode = "Role Name";
        String roleDescription = "Role Description";

        role.setDepartmentCode(departmentCode);
        role.setCode(roleCode);
        role.setDescription(roleDescription);
        role.setParentCode(null);

        CreateRoleCommand command = new CreateRoleCommand(role, departmentCode);

        DepartmentEntity department = new DepartmentEntity();
        department.setCode(departmentCode);
        department.setName("Department Name");

        RoleEntity roleEntity = new RoleEntity();
        roleEntity.setCode(roleCode);
        RoleEntity savedRole = new RoleEntity();
        savedRole.setCode(roleCode);
        RoleDTO expectedResponse = new RoleDTO();

        // When
        when(departmentRepository.findByCodeAndStatusNot(departmentCode, DepartmentStatus.DELETED)).thenReturn(Optional.of(department));
        when(roleMapper.mapToEntity(role, department, null)).thenReturn(roleEntity);
        when(roleRepository.save(roleEntity)).thenReturn(savedRole);
        when(roleMapper.mapToDto(savedRole)).thenReturn(expectedResponse);

        ResponseEntity<RoleDTO> response = underTest.handle(command);

        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(expectedResponse, response.getBody());

        verify(roleRepository, never()).findByDepartmentAndCodeAndStatusNot(eq(department), any(), any());
    }

    @Test
    void itShouldCreateNewRole_WithParentRoleName_When_ParentRoleName_IsProvided() {
        // Given
        RoleDTO role = new RoleDTO();
        String departmentCode = "RH";
        String parentRoleCode = "RH";
        String roleCode = "Role Name";
        String roleDescription = "Role Description";

        role.setDepartmentCode(departmentCode);
        role.setCode(roleCode);
        role.setDescription(roleDescription);
        role.setParentCode(parentRoleCode);

        CreateRoleCommand command = new CreateRoleCommand(role, departmentCode);

        DepartmentEntity department = new DepartmentEntity();
        department.setCode(departmentCode);
        department.setName("Department Name");

        RoleEntity roleEntity = new RoleEntity();
        roleEntity.setCode(roleCode);
        RoleEntity parentRole = new RoleEntity();
        parentRole.setCode(parentRoleCode);
        parentRole.setName("Parent Role Name");
        RoleEntity savedRole = new RoleEntity();
        savedRole.setCode(roleCode);
        RoleDTO expectedResponse = new RoleDTO();
        expectedResponse.setParentCode(parentRoleCode);
        expectedResponse.setCode(roleCode);
        expectedResponse.setDescription(roleDescription);

        // When
        when(departmentRepository.findByCodeAndStatusNot(departmentCode, DepartmentStatus.DELETED)).thenReturn(Optional.of(department));
        when(roleRepository.findByDepartmentAndCodeAndStatusNot(department, parentRoleCode, Status.DELETED))
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
