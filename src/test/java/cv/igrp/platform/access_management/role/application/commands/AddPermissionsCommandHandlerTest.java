package cv.igrp.platform.access_management.role.application.commands;

import cv.igrp.platform.access_management.permission.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
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
public class AddPermissionsCommandHandlerTest {

    @InjectMocks
    private AddPermissionsCommandHandler underTest;
    @Mock
    private PermissionEntityRepository permissionRepository;
    @Mock
    private RoleEntityRepository roleRepository;
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
        String roleName = "admin";
        ArrayList<Integer> permissionList = new ArrayList<>();
        AddPermissionsCommand command = new AddPermissionsCommand(permissionList, roleName);
        ArrayList<PermissionEntity> savedPermissions = new ArrayList<>();
        ApplicationEntity application = new ApplicationEntity();
        HashSet<RoleEntity> roles = new HashSet<>();
        Integer permissionId = 1;
        String permissionName = "permissionName";
        String permissionDescription = "permissionDescription";
        savedPermissions.add(new PermissionEntity(permissionId, permissionName, permissionDescription, Status.ACTIVE, application, null, null, roles));

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
        String roleName = "admin";
        Integer permissionId = 1;
        ArrayList<Integer> permissionList = new ArrayList<>();
        AddPermissionsCommand command = new AddPermissionsCommand(permissionList, roleName);
        permissionList.add(permissionId);
        ArrayList<PermissionEntity> savedPermissions = new ArrayList<>();
        RoleEntity savedRole = new RoleEntity();
        savedRole.setId(roleId);
        String roleDescription = "Role Name";
        savedRole.setDescription(roleDescription);
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
        String roleName = "admin";
        Integer activePermissionId = 1;
        Integer deletedPermissionId = 2;
        List<Integer> permissionIds = List.of(activePermissionId, deletedPermissionId);
        AddPermissionsCommand command = new AddPermissionsCommand(permissionIds, roleName);

        PermissionEntity activePermission = new PermissionEntity();
        activePermission.setId(activePermissionId);
        activePermission.setStatus(Status.ACTIVE);
        activePermission.setName("Active Permission");

        PermissionEntity deletedPermission = new PermissionEntity();
        deletedPermission.setId(deletedPermissionId);
        deletedPermission.setStatus(Status.DELETED);
        deletedPermission.setName("Deleted Permission");

        List<PermissionEntity> returnedPermissions = List.of(activePermission, deletedPermission);

        RoleEntity role = new RoleEntity();
        role.setId(roleId);
        role.setName(roleName);
        role.setDescription("Test Role");
        role.setStatus(Status.ACTIVE);
        role.setPermissions(new HashSet<>());

        PermissionDTO activePermissionDTO = new PermissionDTO();
        activePermissionDTO.setId(activePermissionId);

        when(permissionRepository.findAllById(permissionIds)).thenReturn(returnedPermissions);
        when(roleRepository.findByNameAndStatusNot(roleName, Status.DELETED)).thenReturn(Optional.of(role));
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
        String roleName = "admin";
        Integer activePermissionId = 1;
        Integer deletedPermissionId = 2;
        List<Integer> permissionList = List.of(activePermissionId, deletedPermissionId);
        AddPermissionsCommand command = new AddPermissionsCommand(permissionList, roleName);

        PermissionEntity activePermission = new PermissionEntity();
        activePermission.setId(activePermissionId);
        activePermission.setStatus(Status.ACTIVE);
        String activePermissionName = "Active Permission";
        activePermission.setName("perm" + activePermissionId);
        activePermission.setDescription(activePermissionName);

        PermissionEntity deletedPermission = new PermissionEntity();
        deletedPermission.setId(deletedPermissionId);
        deletedPermission.setStatus(Status.DELETED);
        String deletedPermissionName = "Deleted Permission";
        deletedPermission.setName("perm" + deletedPermissionId);
        deletedPermission.setDescription(deletedPermissionName);

        List<PermissionEntity> savedPermissions = List.of(activePermission);

        RoleEntity savedRole = new RoleEntity();
        savedRole.setId(roleId);
        savedRole.setName(roleName);
        savedRole.setDescription("Role Name");
        savedRole.setStatus(Status.ACTIVE);
        savedRole.setPermissions(new HashSet<>());

        PermissionDTO permissionDTO = new PermissionDTO();
        permissionDTO.setId(activePermissionId);
        when(permissionRepository.findAllById(permissionList)).thenReturn(savedPermissions);
        when(roleRepository.findByNameAndStatusNot(roleName, Status.DELETED)).thenReturn(Optional.of(savedRole));
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
        String roleName = "admin";
        Integer permissionId = 1;
        String permissionName = "perm1";
        List<Integer> permissionIds = List.of(permissionId);
        AddPermissionsCommand command = new AddPermissionsCommand(permissionIds, roleName);

        PermissionEntity activePermission = new PermissionEntity();
        activePermission.setId(permissionId);
        activePermission.setName(permissionName);
        activePermission.setStatus(Status.ACTIVE);

        RoleEntity savedRole = new RoleEntity();
        savedRole.setId(roleId);
        savedRole.setName(roleName);
        savedRole.setPermissions(new HashSet<>(Set.of(activePermission)));

        PermissionDTO permissionDTO = new PermissionDTO();
        permissionDTO.setId(permissionId);
        permissionDTO.setName(permissionName);

        when(permissionRepository.findAllById(permissionIds)).thenReturn(List.of(activePermission));
        when(roleRepository.findByNameAndStatusNot(roleName, Status.DELETED)).thenReturn(Optional.of(savedRole));
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