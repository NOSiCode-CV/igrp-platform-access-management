package cv.igrp.platform.access_management.role.application.commands;

import cv.igrp.platform.access_management.permission.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;
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
    private RoleMapper roleMapper;


    @Test
    void itShouldStartContext() {
        assertNotNull(underTest);
    }

    @Test
    void itShouldThrow_NotFoundException_WhenProvided_RoleId_NotFound() {
        //... Given
        String roleName = "admin";
        ArrayList<String> rolesToRemove = new ArrayList<>();
        RemovePermissionsCommand command = new RemovePermissionsCommand(rolesToRemove, roleName);

        when(roleRepository.findByNameAndStatusNot(roleName, Status.DELETED))
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
        String roleName = "admin";
        int roleId1 = 1;
        int roleId2 = 2;
        String roleName1 = "role1";
        String roleName2 = "role2";
        RoleEntity role1 = new RoleEntity();
        RoleEntity role2 = new RoleEntity();
        role1.setId(roleId1);
        role2.setId(roleId2);
        role1.setName(roleName1);
        role1.setStatus(Status.ACTIVE);
        role2.setName(roleName2);
        role2.setStatus(Status.ACTIVE);

        List<String> rolesToRemove = List.of(roleName1, roleName2);
        RemovePermissionsCommand removePermissionsCommand =
                new RemovePermissionsCommand(rolesToRemove, roleName);

        //... When
        ResponseEntity<List<PermissionDTO>> result = underTest.handle(removePermissionsCommand);

        //... Then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        List<PermissionDTO> responseBody = result.getBody();
        assertNotNull(responseBody);
        assertEquals(2, responseBody.size());

        verify(roleMapper).toDTO(role1);
        verify(roleMapper).toDTO(role2);
    }

    @Test
    void itShouldReturnEmptyList_WhenNoneOfPermissionsAreInRole() {
        //... Given
        int roleId = 1;
        String roleName = "admin";
        int roleId1 = 1;
        int roleId2 = 2;
        int roleId3 = 3;
        String roleName1 = "role1";
        String roleName2 = "role2";
        String roleName3 = "role3";
        RoleEntity savedRole = new RoleEntity();
        savedRole.setStatus(Status.ACTIVE);
        savedRole.setId(roleId);

        RoleEntity role1 = new RoleEntity();
        RoleEntity role2 = new RoleEntity();
        RoleEntity role3 = new RoleEntity();

        role1.setId(roleId1);
        role1.setName(roleName1);
        role2.setId(roleId2);
        role2.setName(roleName2);
        role3.setId(roleId3);
        role3.setName(roleName3);

        role1.setStatus(Status.ACTIVE);
        role2.setStatus(Status.ACTIVE);
        role3.setStatus(Status.ACTIVE);
        List<String> rolesToRemove = List.of(roleName1, roleName2);
        RemovePermissionsCommand removePermissionsCommand =
                new RemovePermissionsCommand(rolesToRemove, roleName);

        //... When
        ResponseEntity<List<PermissionDTO>> result = underTest.handle(removePermissionsCommand);

        //... Then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        MenuEntryDTO responseBody = result.getBody();
        assertNotNull(responseBody);
        assertEquals(0, responseBody.size());


        verify(roleRepository).save(savedRole);
        verify(permissionMapper, never()).mapToDTO(permission1);
        verify(permissionMapper, never()).mapToDTO(permission2);
    }

    @Test
    void itShouldRemoveOnlyExistingPermissions_AndIgnoreOthers() {
        // Given
        int roleId = 1;
        String roleName = "admin";
        Integer permissionToRemoveId = 100;
        String permissionToRemoveName = "permissionToRemove";
        Integer permissionToKeepId = 200;
        String permissionToKeepName = "permissionToKeep";

        RemovePermissionsCommand command = new RemovePermissionsCommand(
                List.of(permissionToRemoveName), roleName);

        PermissionEntity permissionToRemove = new PermissionEntity();
        permissionToRemove.setId(permissionToRemoveId);
        permissionToRemove.setName(permissionToRemoveName);

        PermissionEntity permissionToKeep = new PermissionEntity();
        permissionToKeep.setId(permissionToKeepId);
        permissionToKeep.setName(permissionToKeepName);

        PermissionDTO dtoRemoved = new PermissionDTO();
        dtoRemoved.setId(permissionToRemoveId);
        dtoRemoved.setName(permissionToRemoveName);

        Set<PermissionEntity> initialPermissions = new HashSet<>(Set.of(permissionToRemove, permissionToKeep));

        RoleEntity role = new RoleEntity();
        role.setId(roleId);
        role.setStatus(Status.ACTIVE);
        role.setPermissions(initialPermissions);

        when(roleRepository.findByNameAndStatusNot(roleName, Status.DELETED)).thenReturn(Optional.of(role));
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
        String roleName = "admin";
        List<String> permissionIdsToRemove = List.of("read", "write");
        RemovePermissionsCommand command = new RemovePermissionsCommand(permissionIdsToRemove, roleName);

        RoleEntity role = new RoleEntity();
        role.setId(roleId);
        role.setName(roleName);
        role.setStatus(Status.ACTIVE);
        role.setPermissions(new HashSet<>());

        when(roleRepository.findByNameAndStatusNot(roleName, Status.DELETED)).thenReturn(Optional.of(role));
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
        String roleName = "admin";
        Integer duplicatedPermissionId = 100;
        String duplicatedPermissionName = "duplicatedPermission";

        RemovePermissionsCommand command = new RemovePermissionsCommand(
                List.of(duplicatedPermissionName, duplicatedPermissionName), roleName);

        PermissionEntity permission = new PermissionEntity();
        permission.setId(duplicatedPermissionId);
        permission.setName(duplicatedPermissionName);

        PermissionDTO permissionDTO = new PermissionDTO();
        permissionDTO.setId(duplicatedPermissionId);
        permissionDTO.setName(duplicatedPermissionName);

        Set<PermissionEntity> rolePermissions = new HashSet<>(Set.of(permission));

        RoleEntity role = new RoleEntity();
        role.setId(roleId);
        role.setName(roleName);
        role.setStatus(Status.ACTIVE);
        role.setPermissions(rolePermissions);

        when(roleRepository.findByNameAndStatusNot(roleName, Status.DELETED)).thenReturn(Optional.of(role));
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
        String roleName = "admin";
        Integer permissionToRemoveId = 100;
        String permissionToRemoveName = "permissionToRemove";
        Integer permissionToKeepId = 200;
        String permissionToKeepName = "permissionToKeep";

        RemovePermissionsCommand command = new RemovePermissionsCommand(
                List.of(permissionToRemoveName), roleName);

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
        role.setName(roleName);
        role.setStatus(Status.ACTIVE);
        role.setPermissions(rolePermissions);

        when(roleRepository.findByNameAndStatusNot(roleName, Status.DELETED)).thenReturn(Optional.of(role));
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
