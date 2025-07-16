package cv.igrp.platform.access_management.role.application.commands;

import cv.igrp.platform.access_management.permission.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
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
    private PermissionMapper permissionMapper;


    @Test
    void itShouldStartContext() {
        assertNotNull(underTest);
    }

    @Test
    void itShouldThrow_NotFoundException_WhenProvided_RoleId_NotFound() {
        //... Given
        int roleId = 1;
        ArrayList<Integer> permissionsToRemove = new ArrayList<>();
        RemovePermissionsCommand command = new RemovePermissionsCommand(permissionsToRemove, roleId);

        when(roleRepository.findByIdAndStatusNot(roleId, Status.DELETED))
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
        int permissionId1 = 1;
        int permissionId2 = 2;
        RoleEntity savedRole = new RoleEntity();
        savedRole.setStatus(Status.ACTIVE);
        savedRole.setId(roleId);
        PermissionEntity permission1 = new PermissionEntity();
        PermissionEntity permission2 = new PermissionEntity();
        permission1.setId(permissionId1);
        permission2.setId(permissionId2);
        permission1.setStatus(Status.ACTIVE);
        permission2.setStatus(Status.ACTIVE);

        HashSet<PermissionEntity> permissions = new HashSet<>(Set.of(permission1, permission2));
        savedRole.setPermissions(permissions);
        List<Integer> permissionsToRemove = List.of(permissionId1, permissionId2);
        RemovePermissionsCommand removePermissionsCommand =
                new RemovePermissionsCommand(permissionsToRemove, roleId);

        when(roleRepository.findByIdAndStatusNot(roleId, Status.DELETED))
                .thenReturn(Optional.of(savedRole));
        //... When
        ResponseEntity<List<PermissionDTO>> result = underTest.handle(removePermissionsCommand);

        //... Then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        List<PermissionDTO> responseBody = result.getBody();
        assertNotNull(responseBody);
        assertEquals(2, responseBody.size());

        assertFalse(savedRole.getPermissions().contains(permission1));
        assertFalse(savedRole.getPermissions().contains(permission2));

        verify(roleRepository).save(savedRole);
        verify(permissionMapper).mapToDTO(permission1);
        verify(permissionMapper).mapToDTO(permission2);
    }

    @Test
    void itShouldReturnEmptyList_WhenNoneOfPermissionsAreInRole() {
        //... Given
        int roleId = 1;
        int permissionId1 = 1;
        int permissionId2 = 2;
        int permissionId3 = 3;
        RoleEntity savedRole = new RoleEntity();
        savedRole.setStatus(Status.ACTIVE);
        savedRole.setId(roleId);

        PermissionEntity permission1 = new PermissionEntity();
        PermissionEntity permission2 = new PermissionEntity();
        PermissionEntity permission3 = new PermissionEntity();

        permission1.setId(permissionId1);
        permission2.setId(permissionId2);
        permission3.setId(permissionId3);

        permission1.setStatus(Status.ACTIVE);
        permission2.setStatus(Status.ACTIVE);
        permission3.setStatus(Status.ACTIVE);

        HashSet<PermissionEntity> permissions = new HashSet<>(Set.of(permission3));
        savedRole.setPermissions(permissions);
        List<Integer> permissionsToRemove = List.of(permissionId1, permissionId2);
        RemovePermissionsCommand removePermissionsCommand =
                new RemovePermissionsCommand(permissionsToRemove, roleId);

        when(roleRepository.findByIdAndStatusNot(roleId, Status.DELETED))
                .thenReturn(Optional.of(savedRole));
        //... When
        ResponseEntity<List<PermissionDTO>> result = underTest.handle(removePermissionsCommand);

        //... Then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        List<PermissionDTO> responseBody = result.getBody();
        assertNotNull(responseBody);
        assertEquals(0, responseBody.size());

        assertFalse(savedRole.getPermissions().contains(permission1));
        assertFalse(savedRole.getPermissions().contains(permission2));

        verify(roleRepository).save(savedRole);
        verify(permissionMapper, never()).mapToDTO(permission1);
        verify(permissionMapper, never()).mapToDTO(permission2);
    }

    @Test
    void itShouldRemoveOnlyExistingPermissions_AndIgnoreOthers() {
        // Given
        int roleId = 1;
        Integer permissionToRemoveId = 100;
        Integer permissionToKeepId = 200;

        RemovePermissionsCommand command = new RemovePermissionsCommand(
                List.of(permissionToRemoveId), roleId);

        PermissionEntity permissionToRemove = new PermissionEntity();
        permissionToRemove.setId(permissionToRemoveId);

        PermissionEntity permissionToKeep = new PermissionEntity();
        permissionToKeep.setId(permissionToKeepId);

        PermissionDTO dtoRemoved = new PermissionDTO();
        dtoRemoved.setId(permissionToRemoveId);

        Set<PermissionEntity> initialPermissions = new HashSet<>(Set.of(permissionToRemove, permissionToKeep));

        RoleEntity role = new RoleEntity();
        role.setId(roleId);
        role.setStatus(Status.ACTIVE);
        role.setPermissions(initialPermissions);

        when(roleRepository.findByIdAndStatusNot(roleId, Status.DELETED)).thenReturn(Optional.of(role));
        when(permissionMapper.mapToDTO(permissionToRemove)).thenReturn(dtoRemoved);
        when(roleRepository.save(role)).thenReturn(role);

        // When
        ResponseEntity<List<PermissionDTO>> result = underTest.handle(command);

        // Then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        List<PermissionDTO> responseBody = result.getBody();
        assertNotNull(responseBody);
        assertEquals(1, responseBody.size());
        assertEquals(permissionToRemoveId, responseBody.getFirst().getId());

        assertFalse(role.getPermissions().contains(permissionToRemove));
        assertTrue(role.getPermissions().contains(permissionToKeep));

        verify(permissionMapper).mapToDTO(permissionToRemove);
        verify(roleRepository).save(role);
    }

    @Test
    void itShouldNotFail_WhenRoleHasNoPermissions() {
        // Given
        int roleId = 1;
        List<Integer> permissionIdsToRemove = List.of(101, 102);
        RemovePermissionsCommand command = new RemovePermissionsCommand(permissionIdsToRemove, roleId);

        RoleEntity role = new RoleEntity();
        role.setId(roleId);
        role.setStatus(Status.ACTIVE);
        role.setPermissions(new HashSet<>());

        when(roleRepository.findByIdAndStatusNot(roleId, Status.DELETED)).thenReturn(Optional.of(role));
        when(roleRepository.save(role)).thenReturn(role);

        // When
        ResponseEntity<List<PermissionDTO>> response = underTest.handle(command);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<PermissionDTO> responseBody = response.getBody();
        assertNotNull(responseBody);
        assertTrue(responseBody.isEmpty());

        verify(roleRepository).save(role);
        verifyNoInteractions(permissionMapper);
    }

    @Test
    void itShouldHandleDuplicatePermissionIdsInCommand() {
        // Given
        int roleId = 1;
        Integer duplicatedPermissionId = 100;

        RemovePermissionsCommand command = new RemovePermissionsCommand(
                List.of(duplicatedPermissionId, duplicatedPermissionId), roleId);

        PermissionEntity permission = new PermissionEntity();
        permission.setId(duplicatedPermissionId);

        PermissionDTO permissionDTO = new PermissionDTO();
        permissionDTO.setId(duplicatedPermissionId);

        Set<PermissionEntity> rolePermissions = new HashSet<>(Set.of(permission));

        RoleEntity role = new RoleEntity();
        role.setId(roleId);
        role.setStatus(Status.ACTIVE);
        role.setPermissions(rolePermissions);

        when(roleRepository.findByIdAndStatusNot(roleId, Status.DELETED)).thenReturn(Optional.of(role));
        when(permissionMapper.mapToDTO(permission)).thenReturn(permissionDTO);
        when(roleRepository.save(role)).thenReturn(role);

        // When
        ResponseEntity<List<PermissionDTO>> result = underTest.handle(command);

        // Then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        List<PermissionDTO> responseBody = result.getBody();
        assertNotNull(responseBody);
        assertEquals(1, responseBody.size());
        assertEquals(duplicatedPermissionId, responseBody.getFirst().getId());

        assertFalse(role.getPermissions().contains(permission));

        verify(permissionMapper, times(1)).mapToDTO(permission);
        verify(roleRepository).save(role);
    }

    @Test
    void itShouldMapRemovedPermissionsToDTOsOnly() {
        // Given
        int roleId = 1;
        Integer permissionToRemoveId = 100;
        Integer permissionToKeepId = 200;

        RemovePermissionsCommand command = new RemovePermissionsCommand(
                List.of(permissionToRemoveId), roleId);

        PermissionEntity permissionToRemove = new PermissionEntity();
        permissionToRemove.setId(permissionToRemoveId);

        PermissionEntity permissionToKeep = new PermissionEntity();
        permissionToKeep.setId(permissionToKeepId);

        PermissionDTO dtoToRemove = new PermissionDTO();
        dtoToRemove.setId(permissionToRemoveId);

        Set<PermissionEntity> rolePermissions = new HashSet<>(Set.of(permissionToRemove, permissionToKeep));

        RoleEntity role = new RoleEntity();
        role.setId(roleId);
        role.setStatus(Status.ACTIVE);
        role.setPermissions(rolePermissions);

        when(roleRepository.findByIdAndStatusNot(roleId, Status.DELETED)).thenReturn(Optional.of(role));
        when(permissionMapper.mapToDTO(permissionToRemove)).thenReturn(dtoToRemove);
        when(roleRepository.save(role)).thenReturn(role);

        // When
        ResponseEntity<List<PermissionDTO>> result = underTest.handle(command);

        // Then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        List<PermissionDTO> responseBody = result.getBody();
        assertNotNull(responseBody);
        assertEquals(1, responseBody.size());
        assertEquals(dtoToRemove, responseBody.getFirst());

        verify(permissionMapper).mapToDTO(permissionToRemove);
        verify(permissionMapper, never()).mapToDTO(permissionToKeep);

        assertFalse(role.getPermissions().contains(permissionToRemove));
        assertTrue(role.getPermissions().contains(permissionToKeep));

        verify(roleRepository).save(role);
    }
}