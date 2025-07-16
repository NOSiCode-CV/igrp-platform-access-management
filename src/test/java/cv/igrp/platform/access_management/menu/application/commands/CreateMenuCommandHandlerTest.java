package cv.igrp.platform.access_management.menu.application.commands;

import cv.igrp.platform.access_management.menu.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.menu.mapper.MenuEntryMapper;

import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ResourceEntityRepository;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateMenuCommandHandler Tests")
public class CreateMenuCommandHandlerTest {

    @Mock
    private MenuEntryEntityRepository menuEntryRepository;

    @Mock
    private ApplicationEntityRepository applicationRepository;

    @Mock
    private ResourceEntityRepository resourceRepository;

    @Mock
    private MenuEntryMapper menuEntryMapper;

    @InjectMocks
    private CreateMenuCommandHandler createMenuCommandHandler;

    private CreateMenuCommand createMenuCommand(MenuEntryDTO menuEntryDTO) {
        return new CreateMenuCommand(menuEntryDTO);
    }

    private CreateMenuCommand command;
    private MenuEntryEntity menuEntry;
    private MenuEntryDTO dto;
    private ApplicationEntity application;
    private ResourceEntity resource;
    private MenuEntryEntity parentMenu;

    @BeforeEach
    void setUp() {
        dto = new MenuEntryDTO();
        dto.setApplicationId(1);
        dto.setParentId(3);

        command = createMenuCommand(dto);

        menuEntry = new MenuEntryEntity();
        application = new ApplicationEntity();
        resource = new ResourceEntity();
        parentMenu = new MenuEntryEntity();
    }

    @Test
    @DisplayName("should create a menu entry and return 201 Created")
    void testHandle_whenValidInput_shouldCreateMenuEntry() {
        // Arrange
        when(menuEntryMapper.toEntity(dto)).thenReturn(menuEntry);
        when(applicationRepository.findById(1)).thenReturn(Optional.of(application));
        when(resourceRepository.findById(2)).thenReturn(Optional.of(resource));
        when(menuEntryRepository.findById(3)).thenReturn(Optional.of(parentMenu));
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
        verify(applicationRepository, times(1)).findById(1);
        verify(resourceRepository, times(1)).findById(2);
        verify(menuEntryRepository, times(1)).findById(3);
        verify(menuEntryRepository, times(1)).save(menuEntry);
        verify(menuEntryMapper, times(1)).toDTO(menuEntry);
        verifyNoMoreInteractions(menuEntryRepository, applicationRepository, resourceRepository, menuEntryMapper);
    }

    @Test
    @DisplayName("should skip setting resource and parent if null")
    void testHandle_whenResourceAndParentIdAreNull_shouldSkipThem() {
        // Arrange
        dto.setParentId(null);

        when(menuEntryMapper.toEntity(dto)).thenReturn(menuEntry);
        when(applicationRepository.findById(1)).thenReturn(Optional.of(application));
        when(menuEntryRepository.save(menuEntry)).thenReturn(menuEntry);
        when(menuEntryMapper.toDTO(menuEntry)).thenReturn(dto);

        // Act
        ResponseEntity<MenuEntryDTO> response = createMenuCommandHandler.handle(command);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(dto, response.getBody());

        // Verify
        verify(applicationRepository, times(1)).findById(1);
        verify(resourceRepository, never()).findById(anyInt());
        verify(menuEntryRepository, never()).findById(anyInt());
        verify(menuEntryRepository,times(1)).save(menuEntry);
        verify(menuEntryMapper, times(1)).toDTO(menuEntry);
        verify(menuEntryMapper,times(1)).toEntity(dto);
        verifyNoInteractions(resourceRepository);
        verifyNoMoreInteractions(menuEntryRepository);
    }

    @Test
    @DisplayName("should throw EntityNotFoundException when application ID is invalid")
    void testHandle_whenApplicationNotFound_shouldThrowException() {
        // Arrange
        when(applicationRepository.findById(1)).thenReturn(Optional.empty());
        when(menuEntryMapper.toEntity(dto)).thenReturn(menuEntry);

        // Act
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class, () -> createMenuCommandHandler.handle(command));

        // Assert
        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getBody().getStatus());
    }

    @Test
    @DisplayName("should throw EntityNotFoundException when resource ID is invalid")
    void handle_whenResourceNotFound_shouldThrowException() {

        // Arrange
        when(applicationRepository.findById(1)).thenReturn(Optional.of(application));
        when(menuEntryMapper.toEntity(dto)).thenReturn(menuEntry);
        when(resourceRepository.findById(2)).thenReturn(Optional.empty());

        // Act
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class, () -> createMenuCommandHandler.handle(command));

        // Assert
        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getBody().getStatus());
    }

    @Test
    @DisplayName("should throw EntityNotFoundException when parent menu ID is invalid")
    void handle_whenParentMenuNotFound_shouldThrowException() {
        // Arrange
        when(applicationRepository.findById(1)).thenReturn(Optional.of(application));
        when(menuEntryMapper.toEntity(dto)).thenReturn(menuEntry);
        when(resourceRepository.findById(2)).thenReturn(Optional.of(resource));
        when(menuEntryRepository.findById(3)).thenReturn(Optional.empty());

        // Act
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class, () -> createMenuCommandHandler.handle(command));
        // Assert
        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getBody().getStatus());
    }
}
