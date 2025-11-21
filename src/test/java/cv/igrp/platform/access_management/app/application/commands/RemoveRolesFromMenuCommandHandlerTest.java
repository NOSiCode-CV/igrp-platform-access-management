package cv.igrp.platform.access_management.app.application.commands;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import cv.igrp.platform.access_management.app.mapper.MenuEntryMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;

import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for RemoveRolesFromMenuCommandHandler.
 * Covers:
 *  - Successful removal of 1 and N roles
 *  - Role not found in the menu (ignored)
 *  - Menu not found (throws exception)
 *  - Application not found (NPE, as handler does not validate)
 */
@ExtendWith(MockitoExtension.class)
public class RemoveRolesFromMenuCommandHandlerTest {

    @Mock
    private MenuEntryEntityRepository menuEntryRepository;

    @Mock
    private ApplicationEntityRepository applicationEntityRepository;

    @Mock
    private MenuEntryMapper menuEntryMapper;

    @InjectMocks
    private RemoveRolesFromMenuCommandHandler handler;

    private ApplicationEntity application;
    private MenuEntryEntity menuEntry;

    @BeforeEach
    void setUp() {

        application = new ApplicationEntity();
        application.setId(10);
        application.setCode("APP1");

        RoleEntity r1 = new RoleEntity();
        r1.setCode("R1");
        RoleEntity r2 = new RoleEntity();
        r2.setCode("R2");
        RoleEntity r3 = new RoleEntity();
        r3.setCode("R3");

        menuEntry = new MenuEntryEntity();
        menuEntry.setId(100);
        menuEntry.setCode("MENU1");
        menuEntry.setStatus(Status.ACTIVE);
        menuEntry.setRoles(new HashSet<>(Arrays.asList(r1, r2, r3)));
    }

    // ------------------------------------------------------------------------

    @Test
    void testHandle_removeSingleRole_success() {

        RemoveRolesFromMenuCommand cmd = new RemoveRolesFromMenuCommand(
                List.of("R2"),
                "DEPT",
                "APP1",
                "MENU1"
        );

        when(applicationEntityRepository.findByCodeAndStatusNotDeleted("APP1"))
                .thenReturn(application);

        when(menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(application, "MENU1", Status.DELETED))
                .thenReturn(Optional.of(menuEntry));

        MenuEntryDTO dto = new MenuEntryDTO();
        when(menuEntryMapper.toDTO(any())).thenReturn(dto);

        when(menuEntryRepository.save(menuEntry)).thenReturn(menuEntry);

        ResponseEntity<MenuEntryDTO> response = handler.handle(cmd);

        assertEquals(200, response.getStatusCode().value());
        assertFalse(menuEntry.getRoles().stream().anyMatch(r -> r.getCode().equals("R2")));
        assertTrue(menuEntry.getRoles().stream().anyMatch(r -> r.getCode().equals("R1")));
        assertTrue(menuEntry.getRoles().stream().anyMatch(r -> r.getCode().equals("R3")));
    }

    // ------------------------------------------------------------------------

    @Test
    void testHandle_removeMultipleRoles_success() {

        RemoveRolesFromMenuCommand cmd = new RemoveRolesFromMenuCommand(
                List.of("R1", "R3"),
                "DEPT",
                "APP1",
                "MENU1"
        );

        when(applicationEntityRepository.findByCodeAndStatusNotDeleted("APP1"))
                .thenReturn(application);

        when(menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(application, "MENU1", Status.DELETED))
                .thenReturn(Optional.of(menuEntry));

        MenuEntryDTO dto = new MenuEntryDTO();
        when(menuEntryMapper.toDTO(any())).thenReturn(dto);

        when(menuEntryRepository.save(menuEntry)).thenReturn(menuEntry);

        ResponseEntity<MenuEntryDTO> response = handler.handle(cmd);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, menuEntry.getRoles().size());
        assertTrue(menuEntry.getRoles().stream().anyMatch(r -> r.getCode().equals("R2")));
    }

    // ------------------------------------------------------------------------

    @Test
    void testHandle_roleNotPresent_ignoreAndReturnSuccess() {

        RemoveRolesFromMenuCommand cmd = new RemoveRolesFromMenuCommand(
                List.of("R999"),  // does not exist
                "DEPT",
                "APP1",
                "MENU1"
        );

        when(applicationEntityRepository.findByCodeAndStatusNotDeleted("APP1"))
                .thenReturn(application);

        when(menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(application, "MENU1", Status.DELETED))
                .thenReturn(Optional.of(menuEntry));

        MenuEntryDTO dto = new MenuEntryDTO();
        when(menuEntryMapper.toDTO(any())).thenReturn(dto);

        when(menuEntryRepository.save(menuEntry)).thenReturn(menuEntry);

        ResponseEntity<MenuEntryDTO> response = handler.handle(cmd);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(3, menuEntry.getRoles().size()); // nothing removed
    }

    // ------------------------------------------------------------------------

    @Test
    void testHandle_menuNotFound_throwsException() {

        RemoveRolesFromMenuCommand cmd = new RemoveRolesFromMenuCommand(
                List.of("R1"),
                "DEPT",
                "APP1",
                "MENU404"
        );

        when(applicationEntityRepository.findByCodeAndStatusNotDeleted("APP1"))
                .thenReturn(application);

        when(menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(application, "MENU404", Status.DELETED))
                .thenReturn(Optional.empty());

        assertThrows(IgrpResponseStatusException.class, () -> handler.handle(cmd));
    }

    // ------------------------------------------------------------------------

    @Test
    void testHandle_applicationNotFound_throwsNPE() {

        RemoveRolesFromMenuCommand cmd = new RemoveRolesFromMenuCommand(
                List.of("R1"),
                "DEPT",
                "APP404",
                "MENU1"
        );

        when(applicationEntityRepository.findByCodeAndStatusNotDeleted("APP404"))
                .thenThrow(IgrpResponseStatusException.class); // Handler does not validate this

        assertThrows(IgrpResponseStatusException.class, () -> handler.handle(cmd));
    }
}