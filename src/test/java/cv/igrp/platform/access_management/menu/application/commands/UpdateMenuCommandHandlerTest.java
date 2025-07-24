package cv.igrp.platform.access_management.menu.application.commands;

import cv.igrp.platform.access_management.menu.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.menu.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.application.constants.MenuEntryType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UpdateMenuCommandHandler Tests")
public class UpdateMenuCommandHandlerTest {

    @Mock
    private MenuEntryEntityRepository menuEntryRepository;

    @Mock
    private ApplicationEntityRepository applicationRepository;

    @Mock
    private PermissionEntityRepository permissionRepository;

    @Mock
    private MenuEntryMapper menuEntryMapper;

    @InjectMocks
    private UpdateMenuCommandHandler updateMenuCommandHandler;

    private UpdateMenuCommand updateMenuCommand(MenuEntryDTO menuentrydto) {
        return new UpdateMenuCommand(menuentrydto, "MENU1");
    }

    private MenuEntryEntity existingMenu;
    private MenuEntryEntity updatedMenu;
    private MenuEntryDTO dto;
    private UpdateMenuCommand command;
    private ApplicationEntity application;
    private MenuEntryEntity parentMenu;

    @BeforeEach
    void setUp() {
        existingMenu = new MenuEntryEntity();
        existingMenu.setId(1);
        existingMenu.setCode("MENU1");

        updatedMenu = new MenuEntryEntity();
        updatedMenu.setId(1);
        updatedMenu.setCode("MENU1");

        dto = new MenuEntryDTO();
        dto.setName("Updated Name");
        dto.setType(MenuEntryType.MENU_PAGE);
        dto.setPageSlug("edited-page");
        dto.setPosition((short) 1);
        dto.setIcon("fa-icon");
        dto.setStatus(Status.ACTIVE);
        dto.setTarget("_self");
        dto.setUrl("/new-url");
        dto.setApplicationCode("APP");
        dto.setParentCode("MENU0");

        application = new ApplicationEntity();
        parentMenu = new MenuEntryEntity();

        command = updateMenuCommand(dto);
    }

    @Test
    @DisplayName("should update menu entry and return 200 OK")
    void testHandle_shouldUpdateMenuAndReturnOk() {
        // Arrange
        when(menuEntryRepository.findByCode("MENU1")).thenReturn(Optional.of(existingMenu));
        when(applicationRepository.findByCode("APP")).thenReturn(Optional.of(application));
        when(menuEntryRepository.findByCode("MENU0")).thenReturn(Optional.of(parentMenu));
        when(menuEntryRepository.save(existingMenu)).thenReturn(updatedMenu);
        when(menuEntryMapper.toDTO(updatedMenu)).thenReturn(dto);

        // Act
        ResponseEntity<MenuEntryDTO> response = updateMenuCommandHandler.handle(command);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(dto, response.getBody());

        // Verify interactions
        verify(menuEntryRepository, times(1)).findByCode("MENU1");
        verify(applicationRepository, times(1)).findByCode("APP");
        verify(menuEntryRepository, times(1)).findByCode("MENU0");
        verify(menuEntryRepository, times(1)).save(existingMenu);
        verify(menuEntryMapper).toDTO(updatedMenu);
        verifyNoMoreInteractions(menuEntryRepository, applicationRepository, menuEntryMapper);
    }

    @Test
    @DisplayName("should throw exception when menu entry is not found")
    void testHandle_whenMenuNotFound_shouldThrow() {
        // Arrange
        when(menuEntryRepository.findByCode("MENU1")).thenReturn(Optional.empty());

        // Act
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class, () ->
                updateMenuCommandHandler.handle(command));

        // Assert
        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getBody().getStatus());

        assertNotNull(ex.getBody().getProperties());
        assertTrue(ex.getBody().getProperties().getOrDefault("details", "").toString().contains("Menu not found"));

        // Verify
        verify(menuEntryRepository, times(1)).findByCode("MENU1");
        verifyNoMoreInteractions(menuEntryMapper);
    }

    @Test
    @DisplayName("should throw exception when parent menu is not found")
    void testHandle_whenParentNotFound_shouldThrow() {
        // Arrange
        when(menuEntryRepository.findByCode("MENU1")).thenReturn(Optional.of(existingMenu));
        when(menuEntryRepository.findByCode("MENU0")).thenReturn(Optional.empty());

        // Act
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class, () ->
                updateMenuCommandHandler.handle(command));

        // Assert
        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getBody().getStatus());

        assertNotNull(ex.getBody().getProperties());
        assertTrue(ex.getBody().getProperties().getOrDefault("details", "").toString().contains("Parent Menu Entry not found"));

        // Verify
        verify(menuEntryRepository, times(1)).findByCode("MENU1");
        verify(menuEntryRepository, times(1)).findByCode("MENU0");
        verifyNoMoreInteractions(menuEntryMapper);
    }

    @Test
    @DisplayName("should throw exception when application is not found")
    void testHandle_whenApplicationNotFound_shouldThrow() {
        // Arrange
        when(menuEntryRepository.findByCode("MENU1")).thenReturn(Optional.of(existingMenu));
        when(menuEntryRepository.findByCode("MENU0")).thenReturn(Optional.of(parentMenu));
        when(applicationRepository.findByCode("APP")).thenReturn(Optional.empty());

        // Act
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class, () ->
                updateMenuCommandHandler.handle(command));

        // Assert
        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getBody().getStatus());

        assertNotNull(ex.getBody().getProperties());
        assertTrue(ex.getBody().getProperties().getOrDefault("details", "").toString().contains("Application not found"));

        // Verify
        verify(menuEntryRepository, times(1)).findByCode("MENU1");
        verify(menuEntryRepository, times(1)).findByCode("MENU0");
        verifyNoMoreInteractions(menuEntryMapper);
    }
}
