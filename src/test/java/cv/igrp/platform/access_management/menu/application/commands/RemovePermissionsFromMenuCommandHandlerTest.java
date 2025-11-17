package cv.igrp.platform.access_management.menu.application.commands;

import cv.igrp.platform.access_management.menu.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
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
    private ApplicationEntityRepository applicationRepository;

    @Mock
    private MenuEntryMapper menuEntryMapper;

    @Test
    void itShouldStartContext() {
        assertNotNull(removePermissionsFromMenuCommandHandler);
    }

    @Test
    void itShouldThrow_NotFoundException_WhenProvided_MenuEntryId_NotFound() {
        // Given
        String menuEntryCode = "admin";
        String appCode = "APP";

        ApplicationEntity app = new ApplicationEntity();
        app.setCode(appCode);

        ArrayList<String> rolesToRemove = new ArrayList<>();
        RemovePermissionsFromMenuCommand command = new RemovePermissionsFromMenuCommand(rolesToRemove, appCode, menuEntryCode);

        when(applicationRepository.findByCodeAndStatusNotDeleted(appCode)).thenReturn(app);
        when(menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(app, menuEntryCode, Status.DELETED))
                .thenReturn(Optional.empty());

        // When
        IgrpResponseStatusException response = assertThrows(IgrpResponseStatusException.class,
                () -> removePermissionsFromMenuCommandHandler.handle(command));

        // Then
        assertEquals(HttpStatus.NOT_FOUND.value(), response.getBody().getStatus());
    }

    @Test
    void itShouldRemoveRoles_WhenMenuEntryAndRolesExists() {
        // Given
        int menuEntryId = 1;
        String menuEntryCode = "admin";
        String appCode = "APP";
        int roleId1 = 1;
        int roleId2 = 2;
        String roleCode1 = "role1";
        String roleCode2 = "role2";
        
        ApplicationEntity app = new ApplicationEntity();
        app.setCode(appCode);

        MenuEntryEntity savedMenuEntry = new MenuEntryEntity();
        savedMenuEntry.setStatus(Status.ACTIVE);
        savedMenuEntry.setId(menuEntryId);
        
        RoleEntity role1 = new RoleEntity();
        RoleEntity role2 = new RoleEntity();
        role1.setId(roleId1);
        role2.setId(roleId2);
        role1.setCode(roleCode1);
        role1.setStatus(Status.ACTIVE);
        role2.setCode(roleCode2);
        role2.setStatus(Status.ACTIVE);

        HashSet<RoleEntity> roles = new HashSet<>(Set.of(role1, role2));
        savedMenuEntry.setRoles(roles);
        
        List<String> rolesToRemove = List.of(roleCode1, roleCode2);
        RemovePermissionsFromMenuCommand removePermissionsFromMenuCommand =
                new RemovePermissionsFromMenuCommand(rolesToRemove, appCode, menuEntryCode);

        MenuEntryDTO menuEntryDTO = new MenuEntryDTO();
        menuEntryDTO.setId(menuEntryId);
        menuEntryDTO.setCode(menuEntryCode);
        menuEntryDTO.setRoles(new ArrayList<>());

        when(applicationRepository.findByCodeAndStatusNotDeleted(appCode)).thenReturn(app);
        when(menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(app, menuEntryCode, Status.DELETED))
                .thenReturn(Optional.of(savedMenuEntry));
        when(menuEntryRepository.save(savedMenuEntry)).thenReturn(savedMenuEntry);
        when(menuEntryMapper.toDTO(savedMenuEntry)).thenReturn(menuEntryDTO);
        
        // When
        ResponseEntity<MenuEntryDTO> result = removePermissionsFromMenuCommandHandler.handle(removePermissionsFromMenuCommand);

        // Then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        MenuEntryDTO responseBody = result.getBody();
        assertNotNull(responseBody);
        assertEquals(0, responseBody.getRoles().size());

        assertFalse(savedMenuEntry.getRoles().contains(role1));
        assertFalse(savedMenuEntry.getRoles().contains(role2));

        verify(menuEntryRepository).save(savedMenuEntry);
        verify(menuEntryMapper).toDTO(savedMenuEntry);
    }

    @Test
    void itShouldReturnEmptyList_WhenNoneOfRolesAreInMenuEntry() {
        // Given
        int menuEntryId = 1;
        String menuEntryCode = "admin";
        String appCode = "APP";
        ApplicationEntity app = new ApplicationEntity();
        app.setCode(appCode);

        int roleId1 = 1;
        int roleId2 = 2;
        int roleId3 = 3;
        String roleCode1 = "role1";
        String roleCode2 = "role2";
        String roleCode3 = "role3";
        
        MenuEntryEntity savedMenuEntry = new MenuEntryEntity();
        savedMenuEntry.setStatus(Status.ACTIVE);
        savedMenuEntry.setId(menuEntryId);

        RoleEntity role1 = new RoleEntity();
        RoleEntity role2 = new RoleEntity();
        RoleEntity role3 = new RoleEntity();

        role1.setId(roleId1);
        role1.setCode(roleCode1);
        role2.setId(roleId2);
        role2.setCode(roleCode2);
        role3.setId(roleId3);
        role3.setCode(roleCode3);

        role1.setStatus(Status.ACTIVE);
        role2.setStatus(Status.ACTIVE);
        role3.setStatus(Status.ACTIVE);

        HashSet<RoleEntity> roles = new HashSet<>(Set.of(role3));
        savedMenuEntry.setRoles(roles);
        
        List<String> rolesToRemove = List.of(roleCode1, roleCode2);
        RemovePermissionsFromMenuCommand removePermissionsFromMenuCommand =
                new RemovePermissionsFromMenuCommand(rolesToRemove, appCode, menuEntryCode);

        MenuEntryDTO menuEntryDTO = new MenuEntryDTO();
        menuEntryDTO.setId(menuEntryId);
        menuEntryDTO.setCode(menuEntryCode);
        List<String> remainingRoles = new ArrayList<>();
        remainingRoles.add(roleCode3);
        menuEntryDTO.setRoles(remainingRoles);
        
        when(applicationRepository.findByCodeAndStatusNotDeleted(appCode)).thenReturn(app);
        when(menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(app, menuEntryCode, Status.DELETED))
                .thenReturn(Optional.of(savedMenuEntry));
        when(menuEntryRepository.save(savedMenuEntry)).thenReturn(savedMenuEntry);
        when(menuEntryMapper.toDTO(savedMenuEntry)).thenReturn(menuEntryDTO);
        
        // When
        ResponseEntity<MenuEntryDTO> result = removePermissionsFromMenuCommandHandler.handle(removePermissionsFromMenuCommand);

        // Then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        MenuEntryDTO responseBody = result.getBody();
        assertNotNull(responseBody);
        assertEquals(1, responseBody.getRoles().size());
        assertTrue(responseBody.getRoles().contains(roleCode3));

        assertFalse(savedMenuEntry.getRoles().contains(role1));
        assertFalse(savedMenuEntry.getRoles().contains(role2));
        assertTrue(savedMenuEntry.getRoles().contains(role3));

        verify(menuEntryRepository).save(savedMenuEntry);
        verify(menuEntryMapper).toDTO(savedMenuEntry);
    }

    @Test
    void itShouldRemoveOnlyExistingRoles_AndIgnoreOthers() {
        // Given
        int menuEntryId = 1;
        String menuEntryCode = "admin";
        String appCode = "APP";
        ApplicationEntity app = new ApplicationEntity();
        app.setCode(appCode);
        app.setStatus(Status.ACTIVE);

        Integer roleToRemoveId = 100;
        String roleToRemoveCode = "roleToRemove";
        Integer roleToKeepId = 200;
        String roleToKeepCode = "roleToKeep";

        RemovePermissionsFromMenuCommand command = new RemovePermissionsFromMenuCommand(
                List.of(roleToRemoveCode), appCode, menuEntryCode);

        RoleEntity roleToRemove = new RoleEntity();
        roleToRemove.setId(roleToRemoveId);
        roleToRemove.setCode(roleToRemoveCode);

        RoleEntity roleToKeep = new RoleEntity();
        roleToKeep.setId(roleToKeepId);
        roleToKeep.setCode(roleToKeepCode);

        Set<RoleEntity> initialRoles = new HashSet<>(Set.of(roleToRemove, roleToKeep));

        MenuEntryEntity menuEntry = new MenuEntryEntity();
        menuEntry.setId(menuEntryId);
        menuEntry.setStatus(Status.ACTIVE);
        menuEntry.setRoles(initialRoles);

        MenuEntryDTO menuEntryDTO = new MenuEntryDTO();
        menuEntryDTO.setId(menuEntryId);
        menuEntryDTO.setCode(menuEntryCode);
        List<String> remainingRoles = new ArrayList<>();
        remainingRoles.add(roleToKeepCode);
        menuEntryDTO.setRoles(remainingRoles);

        when(applicationRepository.findByCodeAndStatusNotDeleted(appCode)).thenReturn(app);
        when(menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(app, menuEntryCode, Status.DELETED)).thenReturn(Optional.of(menuEntry));
        when(menuEntryRepository.save(menuEntry)).thenReturn(menuEntry);
        when(menuEntryMapper.toDTO(menuEntry)).thenReturn(menuEntryDTO);

        // When
        ResponseEntity<MenuEntryDTO> result = removePermissionsFromMenuCommandHandler.handle(command);

        // Then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        MenuEntryDTO responseBody = result.getBody();
        assertNotNull(responseBody);
        assertEquals(1, responseBody.getRoles().size());
        assertTrue(responseBody.getRoles().contains(roleToKeepCode));

        assertFalse(menuEntry.getRoles().contains(roleToRemove));
        assertTrue(menuEntry.getRoles().contains(roleToKeep));

        verify(menuEntryRepository).save(menuEntry);
        verify(menuEntryMapper).toDTO(menuEntry);
    }

    @Test
    void itShouldNotFail_WhenMenuEntryHasNoRoles() {
        // Given
        int menuEntryId = 1;
        String menuEntryCode = "admin";
        String appCode = "APP";
        ApplicationEntity app = new ApplicationEntity();
        app.setCode(appCode);

        List<String> roleIdsToRemove = List.of("read", "write");
        RemovePermissionsFromMenuCommand command = new RemovePermissionsFromMenuCommand(roleIdsToRemove, appCode, menuEntryCode);

        MenuEntryEntity menuEntry = new MenuEntryEntity();
        menuEntry.setId(menuEntryId);
        menuEntry.setCode(menuEntryCode);
        menuEntry.setStatus(Status.ACTIVE);
        menuEntry.setRoles(new HashSet<>());

        MenuEntryDTO menuEntryDTO = new MenuEntryDTO();
        menuEntryDTO.setId(menuEntryId);
        menuEntryDTO.setCode(menuEntryCode);
        menuEntryDTO.setRoles(new ArrayList<>());

        when(applicationRepository.findByCodeAndStatusNotDeleted(appCode)).thenReturn(app);
        when(menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(app, menuEntryCode, Status.DELETED)).thenReturn(Optional.of(menuEntry));
        when(menuEntryRepository.save(menuEntry)).thenReturn(menuEntry);
        when(menuEntryMapper.toDTO(menuEntry)).thenReturn(menuEntryDTO);

        // When
        ResponseEntity<MenuEntryDTO> response = removePermissionsFromMenuCommandHandler.handle(command);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        MenuEntryDTO responseBody = response.getBody();
        assertNotNull(responseBody);
        assertTrue(responseBody.getRoles().isEmpty());

        verify(menuEntryRepository).save(menuEntry);
        verify(menuEntryMapper).toDTO(menuEntry);
    }

    @Test
    void itShouldHandleDuplicateRoleIdsInCommand() {
        // Given
        int menuEntryId = 1;
        String menuEntryCode = "admin";
        String appCode = "APP";
        ApplicationEntity app = new ApplicationEntity();
        app.setCode(appCode);

        Integer duplicatedRoleId = 100;
        String duplicatedRoleCode = "duplicatedRole";

        RemovePermissionsFromMenuCommand command = new RemovePermissionsFromMenuCommand(
                List.of(duplicatedRoleCode, duplicatedRoleCode), appCode, menuEntryCode);

        RoleEntity role = new RoleEntity();
        role.setId(duplicatedRoleId);
        role.setCode(duplicatedRoleCode);

        Set<RoleEntity> menuEntryRoles = new HashSet<>(Set.of(role));

        MenuEntryEntity menuEntry = new MenuEntryEntity();
        menuEntry.setId(menuEntryId);
        menuEntry.setCode(menuEntryCode);
        menuEntry.setStatus(Status.ACTIVE);
        menuEntry.setRoles(menuEntryRoles);

        MenuEntryDTO menuEntryDTO = new MenuEntryDTO();
        menuEntryDTO.setId(menuEntryId);
        menuEntryDTO.setCode(menuEntryCode);
        menuEntryDTO.setRoles(new ArrayList<>());

        when(applicationRepository.findByCodeAndStatusNotDeleted(appCode)).thenReturn(app);
        when(menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(app, menuEntryCode, Status.DELETED)).thenReturn(Optional.of(menuEntry));
        when(menuEntryRepository.save(menuEntry)).thenReturn(menuEntry);
        when(menuEntryMapper.toDTO(menuEntry)).thenReturn(menuEntryDTO);

        // When
        ResponseEntity<MenuEntryDTO> result = removePermissionsFromMenuCommandHandler.handle(command);

        // Then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        MenuEntryDTO responseBody = result.getBody();
        assertNotNull(responseBody);
        assertEquals(0, responseBody.getRoles().size());

        assertFalse(menuEntry.getRoles().contains(role));

        verify(menuEntryRepository).save(menuEntry);
        verify(menuEntryMapper).toDTO(menuEntry);
    }
}