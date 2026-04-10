package cv.igrp.platform.access_management.department.application.commands;

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
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
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
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
public class AddPermissionsCommandHandlerTest {

    @InjectMocks
    private AddPermissionsCommandHandler underTest;
    @Mock
    private PermissionEntityRepository permissionRepository;
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
    void itShouldThrowException_WhenGivenRole_NotFound() {
        //... Given
        String roleCode = "admin";
        String deptCode = "DEPT_IGRP";
        ArrayList<String> permissionList = new ArrayList<>();
        AddPermissionsCommand command = new AddPermissionsCommand(permissionList, deptCode, roleCode);

        //... When
        DepartmentEntity department = new DepartmentEntity();
        department.setCode(deptCode);
        department.setStatus(DepartmentStatus.ACTIVE);
        when(departmentRepository.findByCodeAndStatusNotDeleted(department.getCode()))
                .thenReturn(department);
        when(roleRepository.findByDepartmentAndCodeAndStatusNot(department, roleCode, Status.DELETED))
                .thenReturn(Optional.empty());
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> underTest.handle(command));

        //... Then
        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getBody().getStatus());
    }

    @Test
    void itShouldThrowException_When_NoPermission_Is_Found() {
        //... Given
        int roleId = 1;
        String roleCode = "admin";
        String deptCode = "DEPT_IGRP";
        String permissionName = "test";
        ArrayList<String> permissionList = new ArrayList<>();
        AddPermissionsCommand command = new AddPermissionsCommand(permissionList, deptCode, roleCode);
        permissionList.add(permissionName);
        ArrayList<PermissionEntity> savedPermissions = new ArrayList<>();
        RoleEntity savedRole = new RoleEntity();
        savedRole.setId(roleId);
        String roleDescription = "Role Name";
        savedRole.setDescription(roleDescription);
        savedRole.setStatus(Status.ACTIVE);
        //... When
        when(permissionRepository.findAllByNameInAndStatusNotDeleted(anyList()))
                .thenReturn(savedPermissions);
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> underTest.handle(command));

        //... Then
        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getBody().getStatus());
    }

    @Test
    void itShouldAddPermissionsToRole_When_RoleIsFound_AndPermission_IsAvailable() {
        // Given
        int roleId = 1;
        String roleCode = "admin";
        String deptCode = "DEPT_IGRP";
        Integer activePermissionId = 1;
        Integer deletedPermissionId = 2;
        String activePermissionName = "test_active";
        String deletedPermissionName = "test_deleted";
        List<String> permissionIds = List.of(activePermissionName, deletedPermissionName);
        AddPermissionsCommand command = new AddPermissionsCommand(permissionIds, deptCode, roleCode);

        PermissionEntity activePermission = new PermissionEntity();
        activePermission.setId(activePermissionId);
        activePermission.setStatus(Status.ACTIVE);
        activePermission.setName("test_active");

        PermissionEntity deletedPermission = new PermissionEntity();
        deletedPermission.setId(deletedPermissionId);
        deletedPermission.setStatus(Status.DELETED);
        deletedPermission.setName("test_deleted");

        List<PermissionEntity> returnedPermissions = List.of(activePermission, deletedPermission);

        RoleEntity role = new RoleEntity();
        role.setId(roleId);
        role.setCode(roleCode);
        role.setDescription("Test Role");
        role.setStatus(Status.ACTIVE);
        role.setPermissions(new HashSet<>());

        RoleDTO roleDTO = new RoleDTO();
        roleDTO.setId(roleId);
        roleDTO.setPermissions(List.of(activePermissionName));

        DepartmentEntity department = new DepartmentEntity();
        department.setCode(deptCode);
        department.setStatus(DepartmentStatus.ACTIVE);

        when(permissionRepository.findAllByNameInAndStatusNotDeleted(anyList())).thenReturn(returnedPermissions);
        when(departmentRepository.findByCodeAndStatusNotDeleted(deptCode)).thenReturn(department);
        when(roleRepository.findByDepartmentAndCodeAndStatusNot(department, roleCode, Status.DELETED)).thenReturn(Optional.of(role));
        when(roleRepository.save(role)).thenReturn(role);
        when(roleMapper.mapToDto(role)).thenReturn(roleDTO);

        // When
        ResponseEntity<RoleDTO> result = underTest.handle(command);

        // Then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(1, result.getBody().getPermissions().size());
        assertEquals(activePermissionName, result.getBody().getPermissions().getFirst());

        assertTrue(role.getPermissions().contains(activePermission));
        assertFalse(role.getPermissions().contains(deletedPermission));

        verify(roleRepository).save(role);
        verify(roleRepository, times(1)).save(role);
        verify(roleMapper, times(1)).mapToDto(role);

        verifyNoMoreInteractions(roleRepository, roleMapper);
    }

    @Test
    void itShouldIgnorePermissions_WithDeletedStatus_WhenAddingToARole() {
        // Given
        int roleId = 1;
        String roleCode = "admin";
        String deptCode = "DEPT_IGRP";
        Integer activePermissionId = 1;
        Integer deletedPermissionId = 2;
        String activePermissionName = "test_active";
        String deletedPermissionName = "test_deleted";
        List<String> permissionList = List.of(activePermissionName, deletedPermissionName);
        AddPermissionsCommand command = new AddPermissionsCommand(permissionList, deptCode, roleCode);

        PermissionEntity activePermission = new PermissionEntity();
        activePermission.setId(activePermissionId);
        activePermission.setStatus(Status.ACTIVE);
        String activePermissionDesc = "Active Permission";
        activePermission.setName(activePermissionName);
        activePermission.setDescription(activePermissionDesc);

        PermissionEntity deletedPermission = new PermissionEntity();
        deletedPermission.setId(deletedPermissionId);
        deletedPermission.setStatus(Status.DELETED);
        String deletedPermissionDesc = "Deleted Permission";
        deletedPermission.setName(deletedPermissionName);
        deletedPermission.setDescription(deletedPermissionDesc);

        List<PermissionEntity> savedPermissions = List.of(activePermission);

        RoleEntity savedRole = new RoleEntity();
        savedRole.setId(roleId);
        savedRole.setCode(roleCode);
        savedRole.setDescription("Role Name");
        savedRole.setStatus(Status.ACTIVE);
        savedRole.setPermissions(new HashSet<>());

        RoleDTO roleDTO = new RoleDTO();
        roleDTO.setId(roleId);
        roleDTO.setPermissions(List.of(activePermissionName));

        DepartmentEntity department = new DepartmentEntity();
        department.setCode(deptCode);
        department.setStatus(DepartmentStatus.ACTIVE);

        PermissionDTO permissionDTO = new PermissionDTO();
        permissionDTO.setId(activePermissionId);
        when(permissionRepository.findAllByNameInAndStatusNotDeleted(anyList())).thenReturn(savedPermissions);
        when(departmentRepository.findByCodeAndStatusNotDeleted(deptCode)).thenReturn(department);
        when(roleRepository.findByDepartmentAndCodeAndStatusNot(department, roleCode, Status.DELETED)).thenReturn(Optional.of(savedRole));
        when(roleRepository.save(savedRole)).thenReturn(savedRole);
        when(roleMapper.mapToDto(savedRole)).thenReturn(roleDTO);

        // When
        ResponseEntity<RoleDTO> result = underTest.handle(command);

        // Then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(1, result.getBody().getPermissions().size());
        assertEquals(activePermissionName, result.getBody().getPermissions().getFirst());

        verify(roleRepository).save(savedRole);
        verify(roleRepository, times(1)).save(savedRole);
        verify(roleMapper, times(1)).mapToDto(savedRole);

        verifyNoMoreInteractions(roleRepository, roleMapper);
    }

    @Test
    void itShouldNotDuplicatePermissions_WhenPermissionAlreadyExistsInRole() {
        // Given
        int roleId = 1;
        String roleCode = "admin";
        String deptCode = "DEPT";
        Integer permissionId = 1;
        String permissionName = "perm1";
        List<String> permissionIds = List.of(permissionName);
        AddPermissionsCommand command = new AddPermissionsCommand(permissionIds, deptCode, roleCode);

        PermissionEntity activePermission = new PermissionEntity();
        activePermission.setId(permissionId);
        activePermission.setName(permissionName);
        activePermission.setStatus(Status.ACTIVE);

        RoleEntity savedRole = new RoleEntity();
        savedRole.setId(roleId);
        savedRole.setCode(roleCode);
        savedRole.setPermissions(new HashSet<>());

        savedRole.getPermissions().add(activePermission);

        RoleDTO roleDTO = new RoleDTO();
        roleDTO.setId(roleId);
        roleDTO.setPermissions(List.of(activePermission.getName()));

        DepartmentEntity department = new DepartmentEntity();

        department.setCode(deptCode);
        department.setStatus(DepartmentStatus.ACTIVE);

        when(permissionRepository.findAllByNameInAndStatusNotDeleted(anyList())).thenReturn(List.of(activePermission));
        when(departmentRepository.findByCodeAndStatusNotDeleted(deptCode)).thenReturn(department);
        when(roleRepository.findByDepartmentAndCodeAndStatusNot(department, roleCode, Status.DELETED)).thenReturn(Optional.of(savedRole));
        when(roleRepository.save(savedRole)).thenReturn(savedRole);
        when(roleMapper.mapToDto(savedRole)).thenReturn(roleDTO);

        // When
        ResponseEntity<RoleDTO> result = underTest.handle(command);

        // Then
        assertNotNull(result.getBody());
        assertEquals(1, result.getBody().getPermissions().size());
        assertEquals(activePermission.getName(), result.getBody().getPermissions().getFirst());
        assertEquals(1, savedRole.getPermissions().size());

        verify(roleRepository).save(savedRole);
        verify(roleRepository, times(1)).save(savedRole);
        verify(roleMapper, times(1)).mapToDto(savedRole);

        verifyNoMoreInteractions(roleRepository, roleMapper);
    }
}
