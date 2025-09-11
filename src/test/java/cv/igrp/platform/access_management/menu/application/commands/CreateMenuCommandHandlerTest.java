package cv.igrp.platform.access_management.menu.application.commands;

import cv.igrp.platform.access_management.menu.application.domain.service.MenuEntryValidator;
import cv.igrp.platform.access_management.menu.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.menu.mapper.MenuEntryMapper;

import cv.igrp.platform.access_management.shared.application.constants.MenuEntryType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.validation.ResourceValidationResponse;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;


import java.util.ArrayList;
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
    private MenuEntryMapper menuEntryMapper;

    @Mock
    private MenuEntryValidator menuEntryValidator;

    @InjectMocks
    private CreateMenuCommandHandler createMenuCommandHandler;

    private CreateMenuCommand createMenuCommand(MenuEntryDTO menuEntryDTO) {
        return new CreateMenuCommand(menuEntryDTO);
    }

    private CreateMenuCommand command;
    private MenuEntryEntity menuEntry;
    private MenuEntryDTO dto;
    private ApplicationEntity application;
    private MenuEntryEntity parentMenu;
    private ResourceValidationResponse resourceValidationResponse;

    @BeforeEach
    void setUp() {
        dto = new MenuEntryDTO();
        dto.setApplicationCode("APP");
        dto.setParentCode("MENU1");
        dto.setType(MenuEntryType.MENU_PAGE);
        dto.setPageSlug("my-page");
        dto.setUrl("/pages/my-page");

        command = createMenuCommand(dto);

        menuEntry = new MenuEntryEntity();
        application = new ApplicationEntity();
        parentMenu = new MenuEntryEntity();
        resourceValidationResponse = new ResourceValidationResponse();
        resourceValidationResponse.setValid(true);
        resourceValidationResponse.setFailureMessage(new ArrayList<>());
    }

    @Test
    @DisplayName("should create a menu entry and return 201 Created")
    void testHandle_whenValidInput_shouldCreateMenuEntry() {
        // Arrange
        when(menuEntryMapper.toEntity(dto)).thenReturn(menuEntry);
        when(applicationRepository.findByCodeAndStatusNot("APP", Status.DELETED)).thenReturn(Optional.of(application));
        when(menuEntryRepository.findByCodeAndStatusNot("MENU1", Status.DELETED)).thenReturn(Optional.of(parentMenu));
        when(menuEntryRepository.save(menuEntry)).thenReturn(menuEntry);
        when(menuEntryMapper.toDTO(menuEntry)).thenReturn(dto);
        when(menuEntryValidator.validateMenuEntryCode(dto)).thenReturn(resourceValidationResponse);

        // Act
        ResponseEntity<MenuEntryDTO> response = createMenuCommandHandler.handle(command);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(dto, response.getBody());

        // Verify
        verify(applicationRepository, times(1)).findByCodeAndStatusNot("APP", Status.DELETED);
        verify(menuEntryRepository, times(1)).findByCodeAndStatusNot("MENU1", Status.DELETED);
        verify(menuEntryRepository, times(1)).save(menuEntry);
        verify(menuEntryMapper, times(1)).toDTO(menuEntry);
        verifyNoMoreInteractions(menuEntryRepository, applicationRepository, menuEntryMapper);
    }

    @Test
    @DisplayName("should skip setting resource and parent if null")
    void testHandle_whenResourceAndParentIdAreNull_shouldSkipThem() {
        // Arrange
        dto.setParentCode(null);

        when(menuEntryMapper.toEntity(dto)).thenReturn(menuEntry);
        when(applicationRepository.findByCodeAndStatusNot("APP", Status.DELETED)).thenReturn(Optional.of(application));
        when(menuEntryRepository.save(menuEntry)).thenReturn(menuEntry);
        when(menuEntryMapper.toDTO(menuEntry)).thenReturn(dto);
        when(menuEntryValidator.validateMenuEntryCode(dto)).thenReturn(resourceValidationResponse);

        // Act
        ResponseEntity<MenuEntryDTO> response = createMenuCommandHandler.handle(command);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(dto, response.getBody());

        // Verify
        verify(applicationRepository, times(1)).findByCodeAndStatusNot("APP", Status.DELETED);
        verify(menuEntryRepository, never()).findById(anyInt());
        verify(menuEntryRepository,times(1)).save(menuEntry);
        verify(menuEntryMapper, times(1)).toDTO(menuEntry);
        verify(menuEntryMapper,times(1)).toEntity(dto);
        verifyNoMoreInteractions(menuEntryRepository);
    }

    @Test
    @DisplayName("should throw EntityNotFoundException when application ID is invalid")
    void testHandle_whenApplicationNotFound_shouldThrowException() {
        // Arrange
        when(applicationRepository.findByCodeAndStatusNot("APP", Status.DELETED)).thenReturn(Optional.empty());
        when(menuEntryMapper.toEntity(dto)).thenReturn(menuEntry);
        when(menuEntryValidator.validateMenuEntryCode(dto)).thenReturn(resourceValidationResponse);

        // Act
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class, () -> createMenuCommandHandler.handle(command));

        // Assert
        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getBody().getStatus());
    }

    @Test
    @DisplayName("should throw EntityNotFoundException when resource ID is invalid")
    void handle_whenResourceNotFound_shouldThrowException() {

        // Arrange
        when(applicationRepository.findByCodeAndStatusNot("APP", Status.DELETED)).thenReturn(Optional.of(application));
        when(menuEntryMapper.toEntity(dto)).thenReturn(menuEntry);
        when(menuEntryValidator.validateMenuEntryCode(dto)).thenReturn(resourceValidationResponse);

        // Act
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class, () -> createMenuCommandHandler.handle(command));

        // Assert
        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getBody().getStatus());
    }

    @Test
    @DisplayName("should throw EntityNotFoundException when parent menu ID is invalid")
    void handle_whenParentMenuNotFound_shouldThrowException() {
        // Arrange
        when(applicationRepository.findByCodeAndStatusNot("APP", Status.DELETED)).thenReturn(Optional.of(application));
        when(menuEntryMapper.toEntity(dto)).thenReturn(menuEntry);
        when(menuEntryRepository.findByCodeAndStatusNot("MENU1", Status.DELETED)).thenReturn(Optional.empty());
        when(menuEntryValidator.validateMenuEntryCode(dto)).thenReturn(resourceValidationResponse);

        // Act
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class, () -> createMenuCommandHandler.handle(command));
        // Assert
        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getBody().getStatus());
    }
}
