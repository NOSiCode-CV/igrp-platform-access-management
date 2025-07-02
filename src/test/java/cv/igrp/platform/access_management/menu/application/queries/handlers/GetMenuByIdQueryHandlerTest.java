package cv.igrp.platform.access_management.menu.application.queries.handlers;

import cv.igrp.platform.access_management.menu.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.menu.application.queries.queries.GetMenuByIdQuery;
import cv.igrp.platform.access_management.menu.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.application.constants.MenuEntryType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.MenuEntry;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.MenuEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetMenuByIdQueryHandler Tests")
public class GetMenuByIdQueryHandlerTest {

    @Mock
    private MenuEntryRepository menuEntryRepository;

    @Mock
    private MenuEntryMapper menuEntryMapper;

    @InjectMocks
    private GetMenuByIdQueryHandler getMenuByIdQueryHandler;

    private GetMenuByIdQuery getMenuByIdQuery(Integer id){
        return new GetMenuByIdQuery(id);
    }

    private MenuEntry menuEntry;
    private MenuEntryDTO dto;

    @BeforeEach
    void setUp() {
        menuEntry = new MenuEntry();
        menuEntry.setId(1);
        menuEntry.setName("Test Menu");
        menuEntry.setType(MenuEntryType.MENU_PAGE);
        menuEntry.setPosition((short) 1);
        menuEntry.setIcon("icon-class");
        menuEntry.setStatus(Status.ACTIVE);
        menuEntry.setTarget("_blank");
        menuEntry.setUrl("/test/url");

        dto = new MenuEntryDTO();
        dto.setId(1);
        dto.setName("Test Menu");
        dto.setType(MenuEntryType.MENU_PAGE);
        dto.setPosition((short) 1);
        dto.setIcon("icon-class");
        dto.setStatus(Status.ACTIVE);
        dto.setTarget("_blank");
        dto.setUrl("/test/url");
    }

    @Test
    @DisplayName("should return 200 OK with MenuEntryDTO if menu exists")
    void testHandle_whenMenuExists_shouldReturnOk() {
        // Arrange
        GetMenuByIdQuery query = getMenuByIdQuery(1);
        when(menuEntryRepository.findById(1)).thenReturn(Optional.of(menuEntry));
        when(menuEntryMapper.toDTO(menuEntry)).thenReturn(dto);

        // Act
        ResponseEntity<MenuEntryDTO> response = getMenuByIdQueryHandler.handle(query);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(Objects.requireNonNull(response.getBody()));
        assertEquals("Test Menu", response.getBody().getName());
        assertEquals(1, response.getBody().getId());
        assertEquals(dto, response.getBody());

        // Verify
        verify(menuEntryRepository,times(1)).findById(1);
        verify(menuEntryMapper).toDTO(menuEntry);
        verifyNoMoreInteractions(menuEntryRepository,menuEntryMapper);
    }

    @Test
    @DisplayName("should throw IgrpResponseStatusException when menu not found")
    void testHandle_whenMenuNotFound_shouldThrowException() {
        // Arrange
        GetMenuByIdQuery query = getMenuByIdQuery(999);
        when(menuEntryRepository.findById(999)).thenReturn(Optional.empty());

        // Act & Assert
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class, () ->
                getMenuByIdQueryHandler.handle(query));

        // Assert
        ProblemDetail problem = ex.getBody();
        assertEquals(HttpStatus.NOT_FOUND.value(), problem.getStatus());

        assertNotNull(problem.getProperties());
        assertTrue(problem.getProperties().getOrDefault("details", "").toString().contains("Menu not found with id: 999"));

        // Verify
        verify(menuEntryRepository, times(1)).findById(999);
        verifyNoMoreInteractions(menuEntryRepository, menuEntryMapper);
    }
}
