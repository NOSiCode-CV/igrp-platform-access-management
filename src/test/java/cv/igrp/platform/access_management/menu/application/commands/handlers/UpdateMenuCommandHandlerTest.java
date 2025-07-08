package cv.igrp.platform.access_management.menu.application.commands.handlers;

import cv.igrp.platform.access_management.menu.application.commands.commands.UpdateMenuCommand;
import cv.igrp.platform.access_management.menu.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.menu.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.application.constants.MenuEntryType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.domain.models.MenuEntry;
import cv.igrp.platform.access_management.shared.domain.models.Resource;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ApplicationRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.MenuEntryRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ResourceRepository;
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
    private MenuEntryRepository menuEntryRepository;

    @Mock
    private ResourceRepository resourceRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private MenuEntryMapper menuEntryMapper;

    @InjectMocks
    private UpdateMenuCommandHandler updateMenuCommandHandler;

    private UpdateMenuCommand updateMenuCommand(MenuEntryDTO menuentrydto) {
        return new UpdateMenuCommand(menuentrydto, 1);
    }

    private MenuEntry existingMenu;
    private MenuEntry updatedMenu;
    private MenuEntryDTO dto;
    private UpdateMenuCommand command;
    private Resource resource;
    private Application application;
    private MenuEntry parentMenu;

    @BeforeEach
    void setUp() {
        existingMenu = new MenuEntry();
        existingMenu.setId(1);

        updatedMenu = new MenuEntry();
        updatedMenu.setId(1);

        dto = new MenuEntryDTO();
        dto.setName("Updated Name");
        dto.setType(MenuEntryType.MENU_PAGE);
        dto.setPosition((short) 1);
        dto.setIcon("fa-icon");
        dto.setStatus(Status.ACTIVE);
        dto.setTarget("_self");
        dto.setUrl("/new-url");
        dto.setApplicationId(100);
        dto.setResourceId(200);
        dto.setParentId(300);

        resource = new Resource();
        application = new Application();
        parentMenu = new MenuEntry();

        command = updateMenuCommand(dto);
    }

    @Test
    @DisplayName("should update menu entry and return 200 OK")
    void testHandle_shouldUpdateMenuAndReturnOk() {
        // Arrange
        when(menuEntryRepository.findById(1)).thenReturn(Optional.of(existingMenu));
        when(applicationRepository.findById(100)).thenReturn(Optional.of(application));
        when(resourceRepository.findById(200)).thenReturn(Optional.of(resource));
        when(menuEntryRepository.findById(300)).thenReturn(Optional.of(parentMenu));
        when(menuEntryRepository.save(existingMenu)).thenReturn(updatedMenu);
        when(menuEntryMapper.toDTO(updatedMenu)).thenReturn(dto);

        // Act
        ResponseEntity<MenuEntryDTO> response = updateMenuCommandHandler.handle(command);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(dto, response.getBody());

        // Verify interactions
        verify(menuEntryRepository, times(1)).findById(1);
        verify(applicationRepository, times(1)).findById(100);
        verify(resourceRepository, times(1)).findById(200);
        verify(menuEntryRepository, times(1)).findById(300);
        verify(menuEntryRepository, times(1)).save(existingMenu);
        verify(menuEntryMapper).toDTO(updatedMenu);
        verifyNoMoreInteractions(menuEntryRepository, applicationRepository, menuEntryMapper);
    }

    @Test
    @DisplayName("should throw exception when menu entry is not found")
    void testHandle_whenMenuNotFound_shouldThrow() {
        // Arrange
        when(menuEntryRepository.findById(1)).thenReturn(Optional.empty());

        // Act
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class, () ->
                updateMenuCommandHandler.handle(command));

        // Assert
        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getBody().getStatus());

        assertNotNull(ex.getBody().getProperties());
        assertTrue(ex.getBody().getProperties().getOrDefault("details", "").toString().contains("Menu not found"));

        // Verify
        verify(menuEntryRepository, times(1)).findById(1);
        verifyNoMoreInteractions(menuEntryMapper);
    }

    @Test
    @DisplayName("should throw exception when parent menu is not found")
    void testHandle_whenParentNotFound_shouldThrow() {
        // Arrange
        when(menuEntryRepository.findById(1)).thenReturn(Optional.of(existingMenu));
        when(menuEntryRepository.findById(300)).thenReturn(Optional.empty());

        // Act
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class, () ->
                updateMenuCommandHandler.handle(command));

        // Assert
        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getBody().getStatus());

        assertNotNull(ex.getBody().getProperties());
        assertTrue(ex.getBody().getProperties().getOrDefault("details", "").toString().contains("Parent MenuEntry not found"));

        // Verify
        verify(menuEntryRepository, times(1)).findById(1);
        verify(menuEntryRepository, times(1)).findById(300);
        verifyNoMoreInteractions(menuEntryMapper);
    }

    @Test
    @DisplayName("should throw exception when application is not found")
    void testHandle_whenApplicationNotFound_shouldThrow() {
        // Arrange
        when(menuEntryRepository.findById(1)).thenReturn(Optional.of(existingMenu));
        when(menuEntryRepository.findById(300)).thenReturn(Optional.of(parentMenu));
        when(resourceRepository.findById(200)).thenReturn(Optional.of(resource));
        when(applicationRepository.findById(100)).thenReturn(Optional.empty());

        // Act
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class, () ->
                updateMenuCommandHandler.handle(command));

        // Assert
        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getBody().getStatus());

        assertNotNull(ex.getBody().getProperties());
        assertTrue(ex.getBody().getProperties().getOrDefault("details", "").toString().contains("Application not found"));

        // Verify
        verify(menuEntryRepository, times(1)).findById(1);
        verify(menuEntryRepository, times(1)).findById(300);
        verify(resourceRepository, times(1)).findById(200);
        verifyNoMoreInteractions(menuEntryMapper);
    }

    @Test
    @DisplayName("should throw exception when resource is not found")
    void testHandle_whenResourceNotFound_shouldThrow() {
        // Arrange
        when(menuEntryRepository.findById(1)).thenReturn(Optional.of(existingMenu));
        when(menuEntryRepository.findById(300)).thenReturn(Optional.of(parentMenu));
        when(resourceRepository.findById(200)).thenReturn(Optional.empty());

        // Act
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class, () ->
                updateMenuCommandHandler.handle(command));

        // Assert
        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getBody().getStatus());

        assertNotNull(ex.getBody().getProperties());
        assertTrue(ex.getBody().getProperties().getOrDefault("details", "").toString().contains("Resource not found"));

        // Verify
        verify(menuEntryRepository, times(1)).findById(1);
        verify(menuEntryRepository, times(1)).findById(300);
        verify(resourceRepository, times(1)).findById(200);
        verifyNoMoreInteractions(menuEntryMapper);
    }
}
