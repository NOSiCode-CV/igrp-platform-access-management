package cv.igrp.platform.access_management.role.application.commands;

import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RemovePermissionsCommandHandlerTest {

    @InjectMocks
    private RemovePermissionsCommandHandler underTest;
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
    void itShouldThrow_NotFoundException_WhenProvided_RoleId_NotFound() {
        //... Given
        String roleCode = "admin";
        ArrayList<String> permissionsToRemove = new ArrayList<>();
        RemovePermissionsCommand command = new RemovePermissionsCommand(permissionsToRemove, "DEPT", roleCode);

        DepartmentEntity department = new DepartmentEntity();
        department.setCode("DEPT");
        department.setStatus(DepartmentStatus.ACTIVE);

        when(departmentRepository.findByCodeAndStatusNotDeleted("DEPT")).thenReturn(department);

        when(roleRepository.findByDepartmentAndCodeAndStatusNot(department, roleCode, Status.DELETED))
                .thenReturn(Optional.empty());

        //... When
        IgrpResponseStatusException response = assertThrows(IgrpResponseStatusException.class,
                () -> underTest.handle(command));

        //... Then
        assertEquals(HttpStatus.NOT_FOUND.value(), response.getBody().getStatus());
    }

    @Test
    void itShouldRemovePermissions_WhenRoleAndPermissionsExists() {
        //... Given
        int roleId = 1;
        String roleCode = "admin";
        int permissionId1 = 1;
        int permissionId2 = 2;
        String permissionName1 = "permission1";
        String permissionName2 = "permission2";
        RoleEntity savedRole = new RoleEntity();
        savedRole.setStatus(Status.ACTIVE);
        savedRole.setId(roleId);
        PermissionEntity permission1 = new PermissionEntity();
        PermissionEntity permission2 = new PermissionEntity();
        permission1.setId(permissionId1);
        permission2.setId(permissionId2);
        permission1.setName(permissionName1);
        permission1.setStatus(Status.ACTIVE);
        permission2.setName(permissionName2);
        permission2.setStatus(Status.ACTIVE);

        RoleDTO roleDTO = new RoleDTO();
        roleDTO.setId(roleId);
        roleDTO.setPermissions(List.of());

        DepartmentEntity department = new DepartmentEntity();
        department.setCode("DEPT");
        department.setStatus(DepartmentStatus.ACTIVE);

        HashSet<PermissionEntity> permissions = new HashSet<>(Set.of(permission1, permission2));

        savedRole.setPermissions(new HashSet<>());

        savedRole.getPermissions().addAll(permissions);

        List<String> permissionsToRemove = List.of(permissionName1, permissionName2);
        RemovePermissionsCommand removePermissionsCommand =
                new RemovePermissionsCommand(permissionsToRemove, "DEPT", roleCode);

        when(departmentRepository.findByCodeAndStatusNotDeleted("DEPT")).thenReturn(department);
        when(roleRepository.findByDepartmentAndCodeAndStatusNot(department, roleCode, Status.DELETED))
                .thenReturn(Optional.of(savedRole));
        when(roleRepository.save(savedRole)).thenReturn(savedRole);
        when(roleMapper.mapToDto(savedRole)).thenReturn(roleDTO);
        //... When
        ResponseEntity<RoleDTO> result = underTest.handle(removePermissionsCommand);

        //... Then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        RoleDTO responseBody = result.getBody();
        assertNotNull(responseBody);
        assertEquals(0, responseBody.getPermissions().size());

        assertFalse(savedRole.getPermissions().contains(permission1));
        assertFalse(savedRole.getPermissions().contains(permission2));

        verify(roleRepository).save(savedRole);
        verify(roleMapper).mapToDto(savedRole);
    }

    @Test
    void itShouldReturnEmptyList_WhenNoneOfPermissionsAreInRole() {
        //... Given
        int roleId = 1;
        String roleCode = "admin";
        int permissionId1 = 1;
        int permissionId2 = 2;
        int permissionId3 = 3;
        String permissionName1 = "permission1";
        String permissionName2 = "permission2";
        String permissionName3 = "permission3";
        RoleEntity savedRole = new RoleEntity();
        savedRole.setStatus(Status.ACTIVE);
        savedRole.setId(roleId);

        PermissionEntity permission1 = new PermissionEntity();
        PermissionEntity permission2 = new PermissionEntity();
        PermissionEntity permission3 = new PermissionEntity();

        permission1.setId(permissionId1);
        permission1.setName(permissionName1);
        permission2.setId(permissionId2);
        permission2.setName(permissionName2);
        permission3.setId(permissionId3);
        permission3.setName(permissionName3);

        permission1.setStatus(Status.ACTIVE);
        permission2.setStatus(Status.ACTIVE);
        permission3.setStatus(Status.ACTIVE);

        RoleDTO roleDTO = new RoleDTO();
        roleDTO.setId(roleId);
        roleDTO.setPermissions(new ArrayList<>());

        HashSet<PermissionEntity> permissions = new HashSet<>(Set.of(permission3));

        savedRole.setPermissions(new HashSet<>());

        savedRole.getPermissions().addAll(permissions);

        List<String> permissionsToRemove = List.of(permissionName1, permissionName2);
        RemovePermissionsCommand removePermissionsCommand =
                new RemovePermissionsCommand(permissionsToRemove, "DEPT", roleCode);

        DepartmentEntity department = new DepartmentEntity();
        department.setCode("DEPT");
        department.setStatus(DepartmentStatus.ACTIVE);

        when(departmentRepository.findByCodeAndStatusNotDeleted("DEPT")).thenReturn(department);
        when(roleRepository.findByDepartmentAndCodeAndStatusNot(department, roleCode, Status.DELETED))
                .thenReturn(Optional.of(savedRole));
        when(roleRepository.save(savedRole)).thenReturn(savedRole);
        when(roleMapper.mapToDto(savedRole)).thenReturn(roleDTO);
        //... When
        ResponseEntity<RoleDTO> result = underTest.handle(removePermissionsCommand);

        //... Then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        RoleDTO responseBody = result.getBody();
        assertNotNull(responseBody);
        assertEquals(0, responseBody.getPermissions().size());

        assertFalse(savedRole.getPermissions().contains(permission1));
        assertFalse(savedRole.getPermissions().contains(permission2));

        verify(roleRepository).save(savedRole);
        verify(roleMapper).mapToDto(savedRole);
    }

    @Test
    void itShouldRemoveOnlyExistingPermissions_AndIgnoreOthers() {
        // Given
        int roleId = 1;
        String roleCode = "admin";
        Integer permissionToRemoveId = 100;
        String permissionToRemoveName = "permissionToRemove";
        Integer permissionToKeepId = 200;
        String permissionToKeepName = "permissionToKeep";

        RemovePermissionsCommand command = new RemovePermissionsCommand(
                List.of(permissionToRemoveName), "DEPT", roleCode);

        PermissionEntity permissionToRemove = new PermissionEntity();
        permissionToRemove.setId(permissionToRemoveId);
        permissionToRemove.setName(permissionToRemoveName);

        PermissionEntity permissionToKeep = new PermissionEntity();
        permissionToKeep.setId(permissionToKeepId);
        permissionToKeep.setName(permissionToKeepName);

        PermissionDTO dtoRemoved = new PermissionDTO();
        dtoRemoved.setId(permissionToRemoveId);
        dtoRemoved.setName(permissionToRemoveName);

        RoleDTO roleDTO = new RoleDTO();
        roleDTO.setId(roleId);
        roleDTO.setPermissions(new ArrayList<>());

        roleDTO.getPermissions().add(permissionToKeepName);

        Set<PermissionEntity> initialPermissions = new HashSet<>(Set.of(permissionToRemove, permissionToKeep));

        RoleEntity role = new RoleEntity();
        role.setId(roleId);
        role.setStatus(Status.ACTIVE);
        role.setPermissions(new HashSet<>());

        role.getPermissions().addAll(initialPermissions);

        DepartmentEntity department = new DepartmentEntity();
        department.setCode("DEPT");
        department.setStatus(DepartmentStatus.ACTIVE);

        when(departmentRepository.findByCodeAndStatusNotDeleted("DEPT")).thenReturn(department);
        when(roleRepository.findByDepartmentAndCodeAndStatusNot(department, roleCode, Status.DELETED)).thenReturn(Optional.of(role));
        when(roleMapper.mapToDto(role)).thenReturn(roleDTO);
        when(roleRepository.save(role)).thenReturn(role);

        // When
        ResponseEntity<RoleDTO> result = underTest.handle(command);

        // Then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        RoleDTO responseBody = result.getBody();
        assertNotNull(responseBody);
        assertEquals(1, responseBody.getPermissions().size());
        assertEquals(permissionToKeepName, responseBody.getPermissions().getFirst());

        assertFalse(role.getPermissions().contains(permissionToRemove));
        assertTrue(role.getPermissions().contains(permissionToKeep));

        verify(roleMapper).mapToDto(role);
        verify(roleRepository).save(role);
    }

    @Test
    void itShouldNotFail_WhenRoleHasNoPermissions() {
        // Given
        int roleId = 1;
        String roleCode = "admin";
        List<String> permissionIdsToRemove = List.of("read", "write");
        RemovePermissionsCommand command = new RemovePermissionsCommand(permissionIdsToRemove, "DEPT", roleCode);

        RoleEntity role = new RoleEntity();
        role.setId(roleId);
        role.setCode(roleCode);
        role.setStatus(Status.ACTIVE);
        role.setPermissions(new HashSet<>());

        RoleDTO roleDTO = new RoleDTO();
        roleDTO.setId(roleId);
        roleDTO.setPermissions(List.of());

        DepartmentEntity department = new DepartmentEntity();
        department.setCode("DEPT");
        department.setStatus(DepartmentStatus.ACTIVE);

        when(departmentRepository.findByCodeAndStatusNotDeleted("DEPT")).thenReturn(department);
        when(roleRepository.findByDepartmentAndCodeAndStatusNot(department, roleCode, Status.DELETED)).thenReturn(Optional.of(role));
        when(roleRepository.save(role)).thenReturn(role);
        when(roleMapper.mapToDto(role)).thenReturn(roleDTO);

        // When
        ResponseEntity<RoleDTO> response = underTest.handle(command);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        RoleDTO responseBody = response.getBody();
        assertNotNull(responseBody);
        assertTrue(responseBody.getPermissions().isEmpty());

        verify(roleRepository).save(role);
        verify(roleMapper).mapToDto(role);
    }

    @Test
    void itShouldHandleDuplicatePermissionIdsInCommand() {
        // Given
        int roleId = 1;
        String roleCode = "admin";
        Integer duplicatedPermissionId = 100;
        String duplicatedPermissionName = "duplicatedPermission";

        RemovePermissionsCommand command = new RemovePermissionsCommand(
                List.of(duplicatedPermissionName, duplicatedPermissionName), "DEPT", roleCode);

        PermissionEntity permission = new PermissionEntity();
        permission.setId(duplicatedPermissionId);
        permission.setName(duplicatedPermissionName);

        PermissionDTO permissionDTO = new PermissionDTO();
        permissionDTO.setId(duplicatedPermissionId);
        permissionDTO.setName(duplicatedPermissionName);

        Set<PermissionEntity> rolePermissions = new HashSet<>(Set.of(permission));

        RoleEntity role = new RoleEntity();
        role.setId(roleId);
        role.setCode(roleCode);
        role.setStatus(Status.ACTIVE);
        role.setPermissions(new HashSet<>());

        role.getPermissions().addAll(rolePermissions);

        RoleDTO roleDTO = new RoleDTO();
        roleDTO.setId(roleId);
        roleDTO.setPermissions(List.of());

        DepartmentEntity department = new DepartmentEntity();
        department.setCode("DEPT");
        department.setStatus(DepartmentStatus.ACTIVE);

        when(departmentRepository.findByCodeAndStatusNotDeleted("DEPT")).thenReturn(department);
        when(roleRepository.findByDepartmentAndCodeAndStatusNot(department, roleCode, Status.DELETED)).thenReturn(Optional.of(role));
        when(roleMapper.mapToDto(role)).thenReturn(roleDTO);
        when(roleRepository.save(role)).thenReturn(role);

        // When
        ResponseEntity<RoleDTO> result = underTest.handle(command);

        // Then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        RoleDTO responseBody = result.getBody();
        assertNotNull(responseBody);
        assertEquals(0, responseBody.getPermissions().size());

        assertFalse(role.getPermissions().contains(permission));

        verify(roleMapper, times(1)).mapToDto(role);
        verify(roleRepository).save(role);
    }

    @Test
    void itShouldMapRemovedPermissionsToDTOsOnly() {
        // Given
        int roleId = 1;
        String roleCode = "admin";
        Integer permissionToRemoveId = 100;
        String permissionToRemoveName = "permissionToRemove";
        Integer permissionToKeepId = 200;
        String permissionToKeepName = "permissionToKeep";

        RemovePermissionsCommand command = new RemovePermissionsCommand(
                List.of(permissionToRemoveName), "DEPT", roleCode);

        PermissionEntity permissionToRemove = new PermissionEntity();
        permissionToRemove.setId(permissionToRemoveId);
        permissionToRemove.setName(permissionToRemoveName);

        PermissionEntity permissionToKeep = new PermissionEntity();
        permissionToKeep.setId(permissionToKeepId);
        permissionToKeep.setName(permissionToKeepName);

        PermissionDTO dtoToRemove = new PermissionDTO();
        dtoToRemove.setId(permissionToRemoveId);
        dtoToRemove.setName(permissionToKeepName);

        Set<PermissionEntity> rolePermissions = new HashSet<>(Set.of(permissionToRemove, permissionToKeep));

        RoleEntity role = new RoleEntity();
        role.setId(roleId);
        role.setCode(roleCode);
        role.setStatus(Status.ACTIVE);
        role.setPermissions(new HashSet<>());

        role.getPermissions().addAll(rolePermissions);

        RoleDTO roleDTO = new RoleDTO();
        roleDTO.setId(roleId);
        roleDTO.setPermissions(List.of(permissionToKeepName));

        DepartmentEntity department = new DepartmentEntity();
        department.setCode("DEPT");
        department.setStatus(DepartmentStatus.ACTIVE);

        when(departmentRepository.findByCodeAndStatusNotDeleted("DEPT")).thenReturn(department);
        when(roleRepository.findByDepartmentAndCodeAndStatusNot(department, roleCode, Status.DELETED)).thenReturn(Optional.of(role));
        when(roleMapper.mapToDto(role)).thenReturn(roleDTO);
        when(roleRepository.save(role)).thenReturn(role);

        // When
        ResponseEntity<RoleDTO> result = underTest.handle(command);

        // Then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        RoleDTO responseBody = result.getBody();
        assertNotNull(responseBody);
        assertEquals(1, responseBody.getPermissions().size());
        assertEquals(permissionToKeepName, responseBody.getPermissions().getFirst());

        verify(roleMapper).mapToDto(role);

        assertFalse(role.getPermissions().contains(permissionToRemove));
        assertTrue(role.getPermissions().contains(permissionToKeep));

        verify(roleRepository).save(role);
    }
}
