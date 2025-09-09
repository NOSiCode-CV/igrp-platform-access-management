package cv.igrp.platform.access_management.menu.application.commands;

import cv.igrp.platform.access_management.permission.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
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
public class AddPermissionsToMenuCommandHandlerTest {

    @InjectMocks
    private AddPermissionsToMenuCommandHandler addPermissionsToMenuCommandHandler;

    @Mock
    private PermissionEntityRepository permissionRepository;

    @Mock
    private MenuEntryEntityRepository menuEntryRepository;

    @Mock
    private PermissionMapper permissionMapper;

    @Test
    void itShouldStartContext() {
        assertNotNull(addPermissionsToMenuCommandHandler);
    }

    @Test
    void itShouldThrowException_WhenGivenMenuEntry_NotFound() {
        //... Given
        String menuEntryCode = "admin";
        ArrayList<String> permissionList = new ArrayList<>();
        AddPermissionsToMenuCommand command = new AddPermissionsToMenuCommand(permissionList, menuEntryCode);

        //... When
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> addPermissionsToMenuCommandHandler.handle(command));

        //... Then
        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getBody().getStatus());
    }

    @Test
    void itShouldThrowException_When_NoPermission_Is_Found() {
        //... Given
        int menuEntryId = 1;
        String menuEntryCode = "admin";
        String permissionName = "test";
        ArrayList<String> permissionList = new ArrayList<>();
        AddPermissionsToMenuCommand command = new AddPermissionsToMenuCommand(permissionList, menuEntryCode);
        permissionList.add(permissionName);
        ArrayList<PermissionEntity> savedPermissions = new ArrayList<>();
        MenuEntryEntity savedMenuEntry = new MenuEntryEntity();
        savedMenuEntry.setId(menuEntryId);
        String menuEntryDescription = "MenuEntry Name";
        savedMenuEntry.setName(menuEntryDescription);
        savedMenuEntry.setStatus(Status.ACTIVE);
        //... When
        when(permissionRepository.findAllByNameIn(permissionList))
                .thenReturn(savedPermissions);
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> addPermissionsToMenuCommandHandler.handle(command));

        //... Then
        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getBody().getStatus());
    }

    @Test
    void itShouldAddPermissionsToMenuEntry_When_MenuEntryIsFound_AndPermission_IsAvailable() {
        // Given
        int menuEntryId = 1;
        String menuEntryCode = "admin";
        Integer activePermissionId = 1;
        Integer deletedPermissionId = 2;
        String activePermissionName = "test_active";
        String deletedPermissionName = "test_deleted";
        List<String> permissionIds = List.of(activePermissionName, deletedPermissionName);
        AddPermissionsToMenuCommand command = new AddPermissionsToMenuCommand(permissionIds, menuEntryCode);

        PermissionEntity activePermission = new PermissionEntity();
        activePermission.setId(activePermissionId);
        activePermission.setStatus(Status.ACTIVE);
        activePermission.setName("test_active");

        PermissionEntity deletedPermission = new PermissionEntity();
        deletedPermission.setId(deletedPermissionId);
        deletedPermission.setStatus(Status.DELETED);
        deletedPermission.setName("test_deleted");

        List<PermissionEntity> returnedPermissions = List.of(activePermission, deletedPermission);

        MenuEntryEntity menuEntry = new MenuEntryEntity();
        menuEntry.setId(menuEntryId);
        menuEntry.setCode(menuEntryCode);
        menuEntry.setName("Test MenuEntry");
        menuEntry.setStatus(Status.ACTIVE);
        menuEntry.setPermissions(new HashSet<>());

        PermissionDTO activePermissionDTO = new PermissionDTO();
        activePermissionDTO.setId(activePermissionId);

        when(permissionRepository.findAllByNameIn(permissionIds)).thenReturn(returnedPermissions);
        when(menuEntryRepository.findByCodeAndStatusNot(menuEntryCode, Status.DELETED)).thenReturn(Optional.of(menuEntry));
        when(menuEntryRepository.save(menuEntry)).thenReturn(menuEntry);
        when(permissionMapper.mapToDTO(activePermission)).thenReturn(activePermissionDTO);

        // When
        ResponseEntity<List<PermissionDTO>> result = addPermissionsToMenuCommandHandler.handle(command);

        // Then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(1, result.getBody().size());
        assertEquals(activePermissionId, result.getBody().getFirst().getId());

        assertTrue(menuEntry.getPermissions().contains(activePermission));
        assertFalse(menuEntry.getPermissions().contains(deletedPermission));

        verify(menuEntryRepository).save(menuEntry);
        verify(menuEntryRepository, times(1)).save(menuEntry);
        verify(permissionMapper, times(1)).mapToDTO(activePermission);

        verifyNoMoreInteractions(menuEntryRepository, permissionMapper);
    }

    @Test
    void itShouldIgnorePermissions_WithDeletedStatus_WhenAddingToAMenuEntry() {
        // Given
        int menuEntryId = 1;
        String menuEntryCode = "admin";
        Integer activePermissionId = 1;
        Integer deletedPermissionId = 2;
        String activePermissionName = "test_active";
        String deletedPermissionName = "test_deleted";
        List<String> permissionList = List.of(activePermissionName, deletedPermissionName);
        AddPermissionsToMenuCommand command = new AddPermissionsToMenuCommand(permissionList, menuEntryCode);

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

        MenuEntryEntity savedMenuEntry = new MenuEntryEntity();
        savedMenuEntry.setId(menuEntryId);
        savedMenuEntry.setCode(menuEntryCode);
        savedMenuEntry.setName("MenuEntry Name");
        savedMenuEntry.setStatus(Status.ACTIVE);
        savedMenuEntry.setPermissions(new HashSet<>());

        PermissionDTO permissionDTO = new PermissionDTO();
        permissionDTO.setId(activePermissionId);
        when(permissionRepository.findAllByNameIn(permissionList)).thenReturn(savedPermissions);
        when(menuEntryRepository.findByCodeAndStatusNot(menuEntryCode, Status.DELETED)).thenReturn(Optional.of(savedMenuEntry));
        when(menuEntryRepository.save(savedMenuEntry)).thenReturn(savedMenuEntry);
        when(permissionMapper.mapToDTO(activePermission)).thenReturn(permissionDTO);

        // When
        ResponseEntity<List<PermissionDTO>> result = addPermissionsToMenuCommandHandler.handle(command);

        // Then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(1, result.getBody().size());
        assertEquals(permissionDTO, result.getBody().getFirst());
        assertEquals(activePermissionId, result.getBody().getFirst().getId());

        verify(menuEntryRepository).save(savedMenuEntry);
        verify(menuEntryRepository, times(1)).save(savedMenuEntry);
        verify(permissionMapper, times(1)).mapToDTO(activePermission);

        verifyNoMoreInteractions(menuEntryRepository, permissionMapper);
    }

    @Test
    void itShouldNotDuplicatePermissions_WhenPermissionAlreadyExistsInMenuEntry() {
        // Given
        int menuEntryId = 1;
        String menuEntryCode = "admin";
        Integer permissionId = 1;
        String permissionName = "perm1";
        List<String> permissionIds = List.of(permissionName);
        AddPermissionsToMenuCommand command = new AddPermissionsToMenuCommand(permissionIds, menuEntryCode);

        PermissionEntity activePermission = new PermissionEntity();
        activePermission.setId(permissionId);
        activePermission.setName(permissionName);
        activePermission.setStatus(Status.ACTIVE);

        MenuEntryEntity savedMenuEntry = new MenuEntryEntity();
        savedMenuEntry.setId(menuEntryId);
        savedMenuEntry.setCode(menuEntryCode);
        savedMenuEntry.setPermissions(new HashSet<>(Set.of(activePermission)));

        PermissionDTO permissionDTO = new PermissionDTO();
        permissionDTO.setId(permissionId);
        permissionDTO.setName(permissionName);

        when(permissionRepository.findAllByNameIn(permissionIds)).thenReturn(List.of(activePermission));
        when(menuEntryRepository.findByCodeAndStatusNot(menuEntryCode, Status.DELETED)).thenReturn(Optional.of(savedMenuEntry));
        when(menuEntryRepository.save(savedMenuEntry)).thenReturn(savedMenuEntry);
        when(permissionMapper.mapToDTO(activePermission)).thenReturn(permissionDTO);

        // When
        ResponseEntity<List<PermissionDTO>> result = addPermissionsToMenuCommandHandler.handle(command);

        // Then
        assertNotNull(result.getBody());
        System.out.println("List: " + result.getBody());
        assertEquals(1, result.getBody().size());
        assertEquals(permissionDTO, result.getBody().getFirst());
        assertEquals(1, savedMenuEntry.getPermissions().size());

        verify(menuEntryRepository).save(savedMenuEntry);
        verify(menuEntryRepository, times(1)).save(savedMenuEntry);
        verify(permissionMapper, times(1)).mapToDTO(activePermission);

        verifyNoMoreInteractions(menuEntryRepository, permissionMapper);
    }
}