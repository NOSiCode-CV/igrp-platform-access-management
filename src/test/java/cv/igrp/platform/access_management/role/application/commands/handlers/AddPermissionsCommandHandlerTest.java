package cv.igrp.platform.access_management.role.application.commands.handlers;

import cv.igrp.platform.access_management.permission.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.role.application.commands.commands.AddPermissionsCommand;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.domain.models.Permission;
import cv.igrp.platform.access_management.shared.domain.models.Role;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.PermissionRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.RoleRepository;
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
public class AddPermissionsCommandHandlerTest {

    @InjectMocks
    private AddPermissionsCommandHandler underTest;
    @Mock
    private PermissionRepository permissionRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PermissionMapper permissionMapper;

    @Test
    void itShouldStartContext() {
        assertNotNull(underTest);
    }

    @Test
    void itShouldThrowException_WhenGivenRole_NotFound() {
        //... Given
        int roleId = 1;
        ArrayList<Integer> permissionList = new ArrayList<>();
        AddPermissionsCommand command = new AddPermissionsCommand(permissionList, roleId);
        ArrayList<Permission> savedPermissions = new ArrayList<>();
        Application application = new Application();
        HashSet<Role> roles = new HashSet<>();
        Integer permissionId = 1;
        String permissionName = "permissionName";
        String permissionDescription = "permissionDescription";
        savedPermissions.add(new Permission(permissionId, permissionName, permissionDescription, Status.ACTIVE, application, roles));

        //... When
        when(permissionRepository.findAllById(permissionList))
                .thenReturn(savedPermissions);
        when(roleRepository.findByIdAndStatusNot(roleId, Status.DELETED))
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
        Integer permissionId = 1;
        ArrayList<Integer> permissionList = new ArrayList<>();
        AddPermissionsCommand command = new AddPermissionsCommand(permissionList, roleId);
        permissionList.add(permissionId);
        ArrayList<Permission> savedPermissions = new ArrayList<>();
        Role savedRole = new Role();
        savedRole.setId(roleId);
        String roleName = "Role Name";
        savedRole.setName(roleName);
        savedRole.setStatus(Status.ACTIVE);
        //... When
        when(permissionRepository.findAllById(permissionList))
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
        Integer activePermissionId = 1;
        Integer deletedPermissionId = 2;
        List<Integer> permissionIds = List.of(activePermissionId, deletedPermissionId);
        AddPermissionsCommand command = new AddPermissionsCommand(permissionIds, roleId);

        Permission activePermission = new Permission();
        activePermission.setId(activePermissionId);
        activePermission.setStatus(Status.ACTIVE);
        activePermission.setName("Active Permission");

        Permission deletedPermission = new Permission();
        deletedPermission.setId(deletedPermissionId);
        deletedPermission.setStatus(Status.DELETED);
        deletedPermission.setName("Deleted Permission");

        List<Permission> returnedPermissions = List.of(activePermission, deletedPermission);

        Role role = new Role();
        role.setId(roleId);
        role.setName("Test Role");
        role.setStatus(Status.ACTIVE);
        role.setPermissions(new HashSet<>());

        PermissionDTO activePermissionDTO = new PermissionDTO();
        activePermissionDTO.setId(activePermissionId);

        when(permissionRepository.findAllById(permissionIds)).thenReturn(returnedPermissions);
        when(roleRepository.findByIdAndStatusNot(roleId, Status.DELETED)).thenReturn(Optional.of(role));
        when(roleRepository.save(role)).thenReturn(role);
        when(permissionMapper.mapToDTO(activePermission)).thenReturn(activePermissionDTO);

        // When
        ResponseEntity<List<PermissionDTO>> result = underTest.handle(command);

        // Then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(1, result.getBody().size());
        assertEquals(activePermissionId, result.getBody().getFirst().getId());

        assertTrue(role.getPermissions().contains(activePermission));
        assertFalse(role.getPermissions().contains(deletedPermission));

        verify(roleRepository).save(role);
        verify(roleRepository, times(1)).save(role);
        verify(permissionMapper, times(1)).mapToDTO(activePermission);

        verifyNoMoreInteractions(roleRepository, permissionMapper);
    }

    @Test
    void itShouldIgnorePermissions_WithDeletedStatus_WhenAddingToARole() {
        // Given
        int roleId = 1;
        Integer activePermissionId = 1;
        Integer deletedPermissionId = 2;
        List<Integer> permissionList = List.of(activePermissionId, deletedPermissionId);
        AddPermissionsCommand command = new AddPermissionsCommand(permissionList, roleId);

        Permission activePermission = new Permission();
        activePermission.setId(activePermissionId);
        activePermission.setStatus(Status.ACTIVE);
        String activePermissionName = "Active Permission";
        activePermission.setName(activePermissionName);

        Permission deletedPermission = new Permission();
        deletedPermission.setId(deletedPermissionId);
        deletedPermission.setStatus(Status.DELETED);
        String deletedPermissionName = "Deleted Permission";
        deletedPermission.setName(deletedPermissionName);

        List<Permission> savedPermissions = List.of(activePermission);

        Role savedRole = new Role();
        savedRole.setId(roleId);
        savedRole.setName("Role Name");
        savedRole.setStatus(Status.ACTIVE);
        savedRole.setPermissions(new HashSet<>());

        PermissionDTO permissionDTO = new PermissionDTO();
        permissionDTO.setId(activePermissionId);
        when(permissionRepository.findAllById(permissionList)).thenReturn(savedPermissions);
        when(roleRepository.findByIdAndStatusNot(roleId, Status.DELETED)).thenReturn(Optional.of(savedRole));
        when(roleRepository.save(savedRole)).thenReturn(savedRole);
        when(permissionMapper.mapToDTO(activePermission)).thenReturn(permissionDTO);

        // When
        ResponseEntity<List<PermissionDTO>> result = underTest.handle(command);

        // Then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(1, result.getBody().size());
        assertEquals(permissionDTO, result.getBody().getFirst());
        assertEquals(activePermissionId, result.getBody().getFirst().getId());

        verify(roleRepository).save(savedRole);
        verify(roleRepository, times(1)).save(savedRole);
        verify(permissionMapper, times(1)).mapToDTO(activePermission);

        verifyNoMoreInteractions(roleRepository, permissionMapper);
    }

    @Test
    void itShouldNotDuplicatePermissions_WhenPermissionAlreadyExistsInRole() {
        // Given
        int roleId = 1;
        Integer permissionId = 1;
        List<Integer> permissionIds = List.of(permissionId);
        AddPermissionsCommand command = new AddPermissionsCommand(permissionIds, roleId);

        Permission activePermission = new Permission();
        activePermission.setId(permissionId);
        activePermission.setStatus(Status.ACTIVE);

        Role savedRole = new Role();
        savedRole.setId(roleId);
        savedRole.setPermissions(new HashSet<>(Set.of(activePermission)));

        PermissionDTO permissionDTO = new PermissionDTO();
        permissionDTO.setId(permissionId);

        when(permissionRepository.findAllById(permissionIds)).thenReturn(List.of(activePermission));
        when(roleRepository.findByIdAndStatusNot(roleId, Status.DELETED)).thenReturn(Optional.of(savedRole));
        when(roleRepository.save(savedRole)).thenReturn(savedRole);
        when(permissionMapper.mapToDTO(activePermission)).thenReturn(permissionDTO);

        // When
        ResponseEntity<List<PermissionDTO>> result = underTest.handle(command);

        // Then
        assertNotNull(result.getBody());
        assertEquals(1, result.getBody().size());
        assertEquals(permissionDTO, result.getBody().getFirst());
        assertEquals(1, savedRole.getPermissions().size());

        verify(roleRepository).save(savedRole);
        verify(roleRepository, times(1)).save(savedRole);
        verify(permissionMapper, times(1)).mapToDTO(activePermission);

        verifyNoMoreInteractions(roleRepository, permissionMapper);
    }
}