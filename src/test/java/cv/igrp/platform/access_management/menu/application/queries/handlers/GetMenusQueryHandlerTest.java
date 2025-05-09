package cv.igrp.platform.access_management.menu.application.queries.handlers;

import cv.igrp.platform.access_management.menu.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.menu.application.queries.queries.GetMenusQuery;
import cv.igrp.platform.access_management.menu.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.application.constants.MenuEntryType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.models.MenuEntry;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.MenuEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetMenusQueryHandler Tests")
public class GetMenusQueryHandlerTest {

    @Mock
    private MenuEntryRepository menuEntryRepository;

    @Mock
    private MenuEntryMapper menuEntryMapper;

    @InjectMocks
    private GetMenusQueryHandler getMenusQueryHandler;

    private GetMenusQuery getMenusQuery(Integer applicationId, String name, String type){
        return new GetMenusQuery(applicationId, name, type);
    }

    private MenuEntry menuEntry;
    private MenuEntryDTO menuEntryDTO;
    private GetMenusQuery query;

    @BeforeEach
    void setUp() {
        menuEntry = new MenuEntry();
        menuEntry.setId(10);
        menuEntry.setName("Dashboard");
        menuEntry.setType(MenuEntryType.MENU_PAGE);
        menuEntry.setPosition((short) 1);
        menuEntry.setIcon("icon-dashboard");
        menuEntry.setStatus(Status.ACTIVE);
        menuEntry.setTarget("_self");
        menuEntry.setUrl("/dashboard");

        menuEntryDTO = new MenuEntryDTO();
        menuEntryDTO.setId(1);
        menuEntryDTO.setName("Dashboard");
        menuEntryDTO.setType(MenuEntryType.MENU_PAGE);
        menuEntryDTO.setPosition((short) 2);
        menuEntryDTO.setIcon("icon-dashboard");
        menuEntryDTO.setStatus(Status.ACTIVE);
        menuEntryDTO.setTarget("_self");
        menuEntryDTO.setUrl("/dashboard");
    }

    @Test
    @DisplayName("should return filtered list when name is provided")
    void testHandle_withNameFilter_shouldReturnMatchingMenus() {
        // Arrange
        query = getMenusQuery(null, "Dashboard", null);
        when(menuEntryRepository.findAll(any(Specification.class))).thenReturn(List.of(menuEntry));
        when(menuEntryMapper.toDTO(menuEntry)).thenReturn(menuEntryDTO);

        // Act
        ResponseEntity<List<MenuEntryDTO>> response = getMenusQueryHandler.handle(query);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, Objects.requireNonNull(response.getBody()).size());
        assertEquals("Dashboard", response.getBody().get(0).getName());

        // Verify
        verify(menuEntryRepository, times(1)).findAll(any(Specification.class));
        verify(menuEntryMapper).toDTO(menuEntry);
        verifyNoMoreInteractions(menuEntryRepository, menuEntryMapper);
    }

    @Test
    @DisplayName("should return filtered list when type is provided")
    void testHandle_withTypeFilter_shouldReturnMatchingMenus() {
        // Arrange
        query = getMenusQuery(null, null, "MENU_PAGE");
        when(menuEntryRepository.findAll(any(Specification.class))).thenReturn(List.of(menuEntry));
        when(menuEntryMapper.toDTO(menuEntry)).thenReturn(menuEntryDTO);

        // Act
        ResponseEntity<List<MenuEntryDTO>> response = getMenusQueryHandler.handle(query);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, Objects.requireNonNull(response.getBody()).size());
        assertEquals("Dashboard", response.getBody().get(0).getName());

        // Verify
        verify(menuEntryRepository, times(1)).findAll(any(Specification.class));
        verify(menuEntryMapper).toDTO(menuEntry);
        verifyNoMoreInteractions(menuEntryRepository, menuEntryMapper);
    }

    @Test
    @DisplayName("should return filtered list when applicationId is provided")
    void testHandle_withApplicationIdFilter_shouldReturnMatchingMenus() {
        // Arrange
        query = getMenusQuery(1, null, null);
        when(menuEntryRepository.findAll(any(Specification.class))).thenReturn(List.of(menuEntry));
        when(menuEntryMapper.toDTO(menuEntry)).thenReturn(menuEntryDTO);

        // Act
        ResponseEntity<List<MenuEntryDTO>> response = getMenusQueryHandler.handle(query);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, Objects.requireNonNull(response.getBody()).size());
        assertEquals("Dashboard", response.getBody().get(0).getName());

        // Verify
        verify(menuEntryRepository, times(1)).findAll(any(Specification.class));
        verify(menuEntryMapper).toDTO(menuEntry);
        verifyNoMoreInteractions(menuEntryRepository, menuEntryMapper);
    }

    @Test
    @DisplayName("should return all when no filters are provided")
    void testHandle_withNoFilters_shouldReturnAllMenus() {
        // Arrange
        query = getMenusQuery(null, null, null);
        when(menuEntryRepository.findAll(any(Specification.class))).thenReturn(List.of(menuEntry));
        when(menuEntryMapper.toDTO(menuEntry)).thenReturn(menuEntryDTO);

        // Act
        ResponseEntity<List<MenuEntryDTO>> response = getMenusQueryHandler.handle(query);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, Objects.requireNonNull(response.getBody()).size());

        verify(menuEntryRepository, times(1)).findAll(any(Specification.class));
        verify(menuEntryMapper).toDTO(menuEntry);
        verifyNoMoreInteractions(menuEntryRepository, menuEntryMapper);
    }

    @Test
    @DisplayName("should return empty list when no match found")
    void testHandle_withNoResults_shouldReturnEmptyList() {
        // Arrange
        query = getMenusQuery(999, null, null);
        when(menuEntryRepository.findAll(any(Specification.class))).thenReturn(List.of());

        // Act
        ResponseEntity<List<MenuEntryDTO>> response = getMenusQueryHandler.handle(query);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(Objects.requireNonNull(response.getBody()).isEmpty());

        verify(menuEntryRepository, times(1)).findAll(any(Specification.class));
        verify(menuEntryMapper, never()).toDTO(any());
    }

    @Tag("TO_CHECK")
    @Test
    @DisplayName("should return exception when type doesnt match MenuEntryType")
    void testHandle_withNotCorrectTypeFilter_shouldReturnException() {
    }
}
