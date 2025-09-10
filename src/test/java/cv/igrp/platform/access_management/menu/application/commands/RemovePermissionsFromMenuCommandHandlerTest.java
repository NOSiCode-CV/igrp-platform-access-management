package cv.igrp.platform.access_management.menu.application.commands;

import cv.igrp.platform.access_management.permission.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
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
public class RemovePermissionsFromMenuCommandHandlerTest {

    @InjectMocks
    private RemovePermissionsFromMenuCommandHandler removePermissionsFromMenuCommandHandler;

    @Mock
    private MenuEntryEntityRepository menuEntryRepository;

    @Mock
    private PermissionMapper permissionMapper;

    @Test
    void itShouldStartContext() {
        assertNotNull(removePermissionsFromMenuCommandHandler);
    }

    @Test
    void itShouldThrow_NotFoundException_WhenProvided_MenuEntryId_NotFound() {
        //... Given
        String menuEntryCode = "admin";
        ArrayList<String> permissionsToRemove = new ArrayList<>();
        RemovePermissionsFromMenuCommand command = new RemovePermissionsFromMenuCommand(permissionsToRemove, menuEntryCode);

        when(menuEntryRepository.findByCodeAndStatusNot(menuEntryCode, Status.DELETED))
                .thenReturn(Optional.empty());

        //... When
        IgrpResponseStatusException response = assertThrows(IgrpResponseStatusException.class,
                () -> removePermissionsFromMenuCommandHandler.handle(command));

        //... Then
        assertEquals(HttpStatus.NOT_FOUND.value(), response.getBody().getStatus());
    }

    @Test
    void itShouldRemovePermissions_WhenMenuEntryAndPermissionsExists() {
        //... Given
        int menuEntryId = 1;
        String menuEntryCode = "admin";
        int permissionId1 = 1;
        int permissionId2 = 2;
        String permissionName1 = "permission1";
        String permissionName2 = "permission2";
        MenuEntryEntity savedMenuEntry = new MenuEntryEntity();
        savedMenuEntry.setStatus(Status.ACTIVE);
        savedMenuEntry.setId(menuEntryId);
        PermissionEntity permission1 = new PermissionEntity();
        PermissionEntity permission2 = new PermissionEntity();
        permission1.setId(permissionId1);
        permission2.setId(permissionId2);
        permission1.setName(permissionName1);
        permission1.setStatus(Status.ACTIVE);
        permission2.setName(permissionName2);
        permission2.setStatus(Status.ACTIVE);

        HashSet<PermissionEntity> permissions = new HashSet<>(Set.of(permission1, permission2));
        savedMenuEntry.setPermissions(permissions);
        List<String> permissionsToRemove = List.of(permissionName1, permissionName2);
        RemovePermissionsFromMenuCommand removePermissionsFromMenuCommand =
                new RemovePermissionsFromMenuCommand(permissionsToRemove, menuEntryCode);

        when(menuEntryRepository.findByCodeAndStatusNot(menuEntryCode, Status.DELETED))
                .thenReturn(Optional.of(savedMenuEntry));
        //... When
        ResponseEntity<List<PermissionDTO>> result = removePermissionsFromMenuCommandHandler.handle(removePermissionsFromMenuCommand);

        //... Then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        List<PermissionDTO> responseBody = result.getBody();
        assertNotNull(responseBody);
        assertEquals(2, responseBody.size());

        assertFalse(savedMenuEntry.getPermissions().contains(permission1));
        assertFalse(savedMenuEntry.getPermissions().contains(permission2));

        verify(menuEntryRepository).save(savedMenuEntry);
        verify(permissionMapper).mapToDTO(permission1);
        verify(permissionMapper).mapToDTO(permission2);
    }

    @Test
    void itShouldReturnEmptyList_WhenNoneOfPermissionsAreInMenuEntry() {
        //... Given
        int menuEntryId = 1;
        String menuEntryCode = "admin";
        int permissionId1 = 1;
        int permissionId2 = 2;
        int permissionId3 = 3;
        String permissionName1 = "permission1";
        String permissionName2 = "permission2";
        String permissionName3 = "permission3";
        MenuEntryEntity savedMenuEntry = new MenuEntryEntity();
        savedMenuEntry.setStatus(Status.ACTIVE);
        savedMenuEntry.setId(menuEntryId);

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

        HashSet<PermissionEntity> permissions = new HashSet<>(Set.of(permission3));
        savedMenuEntry.setPermissions(permissions);
        List<String> permissionsToRemove = List.of(permissionName1, permissionName2);
        RemovePermissionsFromMenuCommand removePermissionsFromMenuCommand =
                new RemovePermissionsFromMenuCommand(permissionsToRemove, menuEntryCode);

        when(menuEntryRepository.findByCodeAndStatusNot(menuEntryCode, Status.DELETED))
                .thenReturn(Optional.of(savedMenuEntry));
        //... When
        ResponseEntity<List<PermissionDTO>> result = removePermissionsFromMenuCommandHandler.handle(removePermissionsFromMenuCommand);

        //... Then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        List<PermissionDTO> responseBody = result.getBody();
        assertNotNull(responseBody);
        assertEquals(0, responseBody.size());

        assertFalse(savedMenuEntry.getPermissions().contains(permission1));
        assertFalse(savedMenuEntry.getPermissions().contains(permission2));

        verify(menuEntryRepository).save(savedMenuEntry);
        verify(permissionMapper, never()).mapToDTO(permission1);
        verify(permissionMapper, never()).mapToDTO(permission2);
    }

    @Test
    void itShouldRemoveOnlyExistingPermissions_AndIgnoreOthers() {
        // Given
        int menuEntryId = 1;
        String menuEntryCode = "admin";
        Integer permissionToRemoveId = 100;
        String permissionToRemoveName = "permissionToRemove";
        Integer permissionToKeepId = 200;
        String permissionToKeepName = "permissionToKeep";

        RemovePermissionsFromMenuCommand command = new RemovePermissionsFromMenuCommand(
                List.of(permissionToRemoveName), menuEntryCode);

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

        MenuEntryEntity menuEntry = new MenuEntryEntity();
        menuEntry.setId(menuEntryId);
        menuEntry.setStatus(Status.ACTIVE);
        menuEntry.setPermissions(initialPermissions);

        when(menuEntryRepository.findByCodeAndStatusNot(menuEntryCode, Status.DELETED)).thenReturn(Optional.of(menuEntry));
        when(permissionMapper.mapToDTO(permissionToRemove)).thenReturn(dtoRemoved);
        when(menuEntryRepository.save(menuEntry)).thenReturn(menuEntry);

        // When
        ResponseEntity<List<PermissionDTO>> result = removePermissionsFromMenuCommandHandler.handle(command);

        // Then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        List<PermissionDTO> responseBody = result.getBody();
        assertNotNull(responseBody);
        assertEquals(1, responseBody.size());
        assertEquals(permissionToRemoveId, responseBody.getFirst().getId());

        assertFalse(menuEntry.getPermissions().contains(permissionToRemove));
        assertTrue(menuEntry.getPermissions().contains(permissionToKeep));

        verify(permissionMapper).mapToDTO(permissionToRemove);
        verify(menuEntryRepository).save(menuEntry);
    }

    @Test
    void itShouldNotFail_WhenMenuEntryHasNoPermissions() {
        // Given
        int menuEntryId = 1;
        String menuEntryCode = "admin";
        List<String> permissionIdsToRemove = List.of("read", "write");
        RemovePermissionsFromMenuCommand command = new RemovePermissionsFromMenuCommand(permissionIdsToRemove, menuEntryCode);

        MenuEntryEntity menuEntry = new MenuEntryEntity();
        menuEntry.setId(menuEntryId);
        menuEntry.setCode(menuEntryCode);
        menuEntry.setStatus(Status.ACTIVE);
        menuEntry.setPermissions(new HashSet<>());

        when(menuEntryRepository.findByCodeAndStatusNot(menuEntryCode, Status.DELETED)).thenReturn(Optional.of(menuEntry));
        when(menuEntryRepository.save(menuEntry)).thenReturn(menuEntry);

        // When
        ResponseEntity<List<PermissionDTO>> response = removePermissionsFromMenuCommandHandler.handle(command);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<PermissionDTO> responseBody = response.getBody();
        assertNotNull(responseBody);
        assertTrue(responseBody.isEmpty());

        verify(menuEntryRepository).save(menuEntry);
        verifyNoInteractions(permissionMapper);
    }

    @Test
    void itShouldHandleDuplicatePermissionIdsInCommand() {
        // Given
        int menuEntryId = 1;
        String menuEntryCode = "admin";
        Integer duplicatedPermissionId = 100;
        String duplicatedPermissionName = "duplicatedPermission";

        RemovePermissionsFromMenuCommand command = new RemovePermissionsFromMenuCommand(
                List.of(duplicatedPermissionName, duplicatedPermissionName), menuEntryCode);

        PermissionEntity permission = new PermissionEntity();
        permission.setId(duplicatedPermissionId);
        permission.setName(duplicatedPermissionName);

        PermissionDTO permissionDTO = new PermissionDTO();
        permissionDTO.setId(duplicatedPermissionId);
        permissionDTO.setName(duplicatedPermissionName);

        Set<PermissionEntity> menuEntryPermissions = new HashSet<>(Set.of(permission));

        MenuEntryEntity menuEntry = new MenuEntryEntity();
        menuEntry.setId(menuEntryId);
        menuEntry.setCode(menuEntryCode);
        menuEntry.setStatus(Status.ACTIVE);
        menuEntry.setPermissions(menuEntryPermissions);

        when(menuEntryRepository.findByCodeAndStatusNot(menuEntryCode, Status.DELETED)).thenReturn(Optional.of(menuEntry));
        when(permissionMapper.mapToDTO(permission)).thenReturn(permissionDTO);
        when(menuEntryRepository.save(menuEntry)).thenReturn(menuEntry);

        // When
        ResponseEntity<List<PermissionDTO>> result = removePermissionsFromMenuCommandHandler.handle(command);

        // Then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        List<PermissionDTO> responseBody = result.getBody();
        assertNotNull(responseBody);
        assertEquals(1, responseBody.size());
        assertEquals(duplicatedPermissionId, responseBody.getFirst().getId());

        assertFalse(menuEntry.getPermissions().contains(permission));

        verify(permissionMapper, times(1)).mapToDTO(permission);
        verify(menuEntryRepository).save(menuEntry);
    }

    @Test
    void itShouldMapRemovedPermissionsToDTOsOnly() {
        // Given
        int menuEntryId = 1;
        String menuEntryCode = "admin";
        Integer permissionToRemoveId = 100;
        String permissionToRemoveName = "permissionToRemove";
        Integer permissionToKeepId = 200;
        String permissionToKeepName = "permissionToKeep";

        RemovePermissionsFromMenuCommand command = new RemovePermissionsFromMenuCommand(
                List.of(permissionToRemoveName), menuEntryCode);

        PermissionEntity permissionToRemove = new PermissionEntity();
        permissionToRemove.setId(permissionToRemoveId);
        permissionToRemove.setName(permissionToRemoveName);

        PermissionEntity permissionToKeep = new PermissionEntity();
        permissionToKeep.setId(permissionToKeepId);
        permissionToKeep.setName(permissionToKeepName);

        PermissionDTO dtoToRemove = new PermissionDTO();
        dtoToRemove.setId(permissionToRemoveId);
        dtoToRemove.setName(permissionToKeepName);

        Set<PermissionEntity> menuEntryPermissions = new HashSet<>(Set.of(permissionToRemove, permissionToKeep));

        MenuEntryEntity menuEntry = new MenuEntryEntity();
        menuEntry.setId(menuEntryId);
        menuEntry.setCode(menuEntryCode);
        menuEntry.setStatus(Status.ACTIVE);
        menuEntry.setPermissions(menuEntryPermissions);

        when(menuEntryRepository.findByCodeAndStatusNot(menuEntryCode, Status.DELETED)).thenReturn(Optional.of(menuEntry));
        when(permissionMapper.mapToDTO(permissionToRemove)).thenReturn(dtoToRemove);
        when(menuEntryRepository.save(menuEntry)).thenReturn(menuEntry);

        // When
        ResponseEntity<List<PermissionDTO>> result = removePermissionsFromMenuCommandHandler.handle(command);

        // Then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        List<PermissionDTO> responseBody = result.getBody();
        assertNotNull(responseBody);
        assertEquals(1, responseBody.size());
        assertEquals(dtoToRemove, responseBody.getFirst());

        verify(permissionMapper).mapToDTO(permissionToRemove);
        verify(permissionMapper, never()).mapToDTO(permissionToKeep);

        assertFalse(menuEntry.getPermissions().contains(permissionToRemove));
        assertTrue(menuEntry.getPermissions().contains(permissionToKeep));

        verify(menuEntryRepository).save(menuEntry);
    }
}