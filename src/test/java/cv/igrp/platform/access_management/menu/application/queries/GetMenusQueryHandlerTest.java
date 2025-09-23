package cv.igrp.platform.access_management.menu.application.queries;

import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.menu.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.application.constants.MenuEntryType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
@DisplayName("GetMenusQueryHandler Tests")
public class GetMenusQueryHandlerTest {

    @Mock
    private MenuEntryEntityRepository menuEntryRepository;

    @Mock
    private MenuEntryMapper menuEntryMapper;

    @InjectMocks
    private GetMenusQueryHandler getMenusQueryHandler;

    private GetMenusQuery getMenusQuery(String applicationCode, String name, String type, String status){
        return new GetMenusQuery(name, type, status, applicationCode, null);
    }

    private MenuEntryEntity menuEntry;
    private MenuEntryDTO menuEntryDTO;
    private GetMenusQuery query;

    @BeforeEach
    void setUp() {
        menuEntry = new MenuEntryEntity();
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
        query = getMenusQuery(null, "Dashboard", null, null);
        when(menuEntryRepository.findAll(any(Specification.class))).thenReturn(List.of(menuEntry));
        when(menuEntryMapper.toDTO(menuEntry)).thenReturn(menuEntryDTO);

        // Act
        ResponseEntity<List<MenuEntryDTO>> response = getMenusQueryHandler.handle(query);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, Objects.requireNonNull(response.getBody()).size());
        assertEquals("Dashboard", response.getBody().getFirst().getName());

        // Verify
        verify(menuEntryRepository, times(1)).findAll(any(Specification.class));
        verify(menuEntryMapper).toDTO(menuEntry);
        verifyNoMoreInteractions(menuEntryRepository, menuEntryMapper);
    }

    @Test
    @DisplayName("should return filtered list when type is provided")
    void testHandle_withTypeFilter_shouldReturnMatchingMenus() {
        // Arrange
        query = getMenusQuery(null, null, "MENU_PAGE", null);
        when(menuEntryRepository.findAll(any(Specification.class))).thenReturn(List.of(menuEntry));
        when(menuEntryMapper.toDTO(menuEntry)).thenReturn(menuEntryDTO);

        // Act
        ResponseEntity<List<MenuEntryDTO>> response = getMenusQueryHandler.handle(query);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, Objects.requireNonNull(response.getBody()).size());
        assertEquals("Dashboard", response.getBody().getFirst().getName());

        // Verify
        verify(menuEntryRepository, times(1)).findAll(any(Specification.class));
        verify(menuEntryMapper).toDTO(menuEntry);
        verifyNoMoreInteractions(menuEntryRepository, menuEntryMapper);
    }

    @Test
    @DisplayName("should return filtered list when status is provided")
    void testHandle_withStatusFilter_shouldReturnMatchingMenus() {
        // Arrange
        query = getMenusQuery(null, null, null, "ACTIVE");
        when(menuEntryRepository.findAll(any(Specification.class))).thenReturn(List.of(menuEntry));
        when(menuEntryMapper.toDTO(menuEntry)).thenReturn(menuEntryDTO);

        // Act
        ResponseEntity<List<MenuEntryDTO>> response = getMenusQueryHandler.handle(query);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, Objects.requireNonNull(response.getBody()).size());
        assertEquals("Dashboard", response.getBody().getFirst().getName());

        // Verify
        verify(menuEntryRepository, times(1)).findAll(any(Specification.class));
        verify(menuEntryMapper).toDTO(menuEntry);
        verifyNoMoreInteractions(menuEntryRepository, menuEntryMapper);
    }

    @Test
    @DisplayName("should return filtered list when applicationId is provided")
    void testHandle_withApplicationIdFilter_shouldReturnMatchingMenus() {
        // Arrange
        query = getMenusQuery("APP", null, null, null);
        when(menuEntryRepository.findAll(any(Specification.class))).thenReturn(List.of(menuEntry));
        when(menuEntryMapper.toDTO(menuEntry)).thenReturn(menuEntryDTO);

        // Act
        ResponseEntity<List<MenuEntryDTO>> response = getMenusQueryHandler.handle(query);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, Objects.requireNonNull(response.getBody()).size());
        assertEquals("Dashboard", response.getBody().getFirst().getName());

        // Verify
        verify(menuEntryRepository, times(1)).findAll(any(Specification.class));
        verify(menuEntryMapper).toDTO(menuEntry);
        verifyNoMoreInteractions(menuEntryRepository, menuEntryMapper);
    }

    @Test
    @DisplayName("should return all when no filters are provided")
    void testHandle_withNoFilters_shouldReturnAllMenus() {
        // Arrange
        query = getMenusQuery(null, null, null, null);
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
        query = getMenusQuery("APP", null, null, null);
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
        // Arrange
        query = getMenusQuery(null, null, "NON_EXISTENT_TYPE", null);

        // Act
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                getMenusQueryHandler.handle(query));

        // Assert
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("Invalid MenuEntryType"));

        // Verify
        verifyNoInteractions(menuEntryRepository);
    }

    @Tag("TO_CHECK")
    @Test
    @DisplayName("should return exception when status doesnt match Status")
    void testHandle_withNotCorrectStatusFilter_shouldReturnException() {
        // Arrange
        query = getMenusQuery(null, null, null,"NON_EXISTENT_STATUS");

        // Act
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                getMenusQueryHandler.handle(query));

        // Assert
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("Invalid Status"));

        // Verify
        verifyNoInteractions(menuEntryRepository);
    }

}
