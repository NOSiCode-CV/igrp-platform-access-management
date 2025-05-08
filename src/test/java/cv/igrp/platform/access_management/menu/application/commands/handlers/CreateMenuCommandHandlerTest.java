package cv.igrp.platform.access_management.menu.application.commands.handlers;

import cv.igrp.platform.access_management.menu.application.commands.commands.CreateMenuCommand;
import cv.igrp.platform.access_management.menu.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.menu.mapper.MenuEntryMapper;

import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.domain.models.MenuEntry;
import cv.igrp.platform.access_management.shared.domain.models.Resource;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ApplicationRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.MenuEntryRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateMenuCommandHandler Tests")
public class CreateMenuCommandHandlerTest {

    @Mock
    private MenuEntryRepository menuEntryRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private ResourceRepository resourceRepository;

    @Mock
    private MenuEntryMapper menuEntryMapper;

    @InjectMocks
    private CreateMenuCommandHandler createMenuCommandHandler;

    private CreateMenuCommand createMenuCommand(MenuEntryDTO menuEntryDTO) {
        return new CreateMenuCommand(menuEntryDTO);
    }

    private CreateMenuCommand command;
    private MenuEntry menuEntry;
    private MenuEntryDTO dto;
    private Application application;
    private Resource resource;
    private MenuEntry parentMenu;

    @BeforeEach
    void setUp() {
        dto = new MenuEntryDTO();
        dto.setApplicationId(1);
        dto.setResourceId(2);
        dto.setParentId(3);

        command = createMenuCommand(dto);

        menuEntry = new MenuEntry();
        application = new Application();
        resource = new Resource();
        parentMenu = new MenuEntry();
    }

    @Test
    @DisplayName("should create a menu entry and return 201 Created")
    void testHandle_whenValidInput_shouldCreateMenuEntry() {
        // Arrange
        when(menuEntryMapper.toEntity(dto)).thenReturn(menuEntry);
        when(applicationRepository.getReferenceById(1)).thenReturn(application);
        when(resourceRepository.getReferenceById(2)).thenReturn(resource);
        when(menuEntryRepository.getReferenceById(3)).thenReturn(parentMenu);
        when(menuEntryRepository.save(menuEntry)).thenReturn(menuEntry);
        when(menuEntryMapper.toDTO(menuEntry)).thenReturn(dto);

        // Act
        ResponseEntity<MenuEntryDTO> response = createMenuCommandHandler.handle(command);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(dto, response.getBody());

        // Verify
        verify(applicationRepository, times(1)).getReferenceById(1);
        verify(resourceRepository, times(1)).getReferenceById(2);
        verify(menuEntryRepository, times(1)).getReferenceById(3);
        verify(menuEntryRepository, times(1)).save(menuEntry);
        verify(menuEntryMapper, times(1)).toDTO(menuEntry);
        verifyNoMoreInteractions(menuEntryRepository, applicationRepository, resourceRepository, menuEntryMapper);
    }

    @Test
    @DisplayName("should skip setting resource and parent if null")
    void testHandle_whenResourceAndParentIdAreNull_shouldSkipThem() {
        // Arrange
        dto.setResourceId(null);
        dto.setParentId(null);

        when(menuEntryMapper.toEntity(dto)).thenReturn(menuEntry);
        when(applicationRepository.getReferenceById(1)).thenReturn(application);
        when(menuEntryRepository.save(menuEntry)).thenReturn(menuEntry);
        when(menuEntryMapper.toDTO(menuEntry)).thenReturn(dto);

        // Act
        ResponseEntity<MenuEntryDTO> response = createMenuCommandHandler.handle(command);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(dto, response.getBody());

        // Verify
        verify(applicationRepository, times(1)).getReferenceById(1);
        verify(resourceRepository, never()).getReferenceById(anyInt());
        verify(menuEntryRepository, never()).getReferenceById(anyInt());
        verify(menuEntryRepository,times(1)).save(menuEntry);
        verify(menuEntryMapper, times(1)).toDTO(menuEntry);
        verify(menuEntryMapper,times(1)).toEntity(dto);
        verifyNoInteractions(resourceRepository);
        verifyNoMoreInteractions(menuEntryRepository);
    }
    @Tag("TO CHECK")
    @Test
    @DisplayName("should throw EntityNotFoundException when application ID is invalid")
    void testHandle_whenApplicationNotFound_shouldThrowException() {
        // Arrange
    }

    @Tag("TO CHECK")
    @Test
    @DisplayName("should throw EntityNotFoundException when resource ID is invalid")
    void handle_whenResourceNotFound_shouldThrowException() {

    }

    @Tag("TO CHECK")
    @Test
    @DisplayName("should throw EntityNotFoundException when parent menu ID is invalid")
    void handle_whenParentMenuNotFound_shouldThrowException() {

    }
}
