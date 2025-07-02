package cv.igrp.platform.access_management.resource.application.queries.handlers;


import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.resource.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.domain.models.Resource;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import cv.igrp.platform.access_management.resource.application.dto.*;
import cv.igrp.platform.access_management.resource.application.queries.queries.*;

import java.util.List;
import java.util.Objects;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
@DisplayName("GetResourcesQueryHandler Unit Tests")
public class GetResourcesQueryHandlerTest {

    @Mock
    private ResourceRepository resourceRepository;

    @Mock
    private ResourceMapper resourceMapper;

    @InjectMocks
    private GetResourcesQueryHandler handler;

    private Resource resource;
    private ResourceDTO resourceDTO;

    @BeforeEach
    void setUp() {
        resource = new Resource();
        resource.setId(1);
        resource.setName("Sample Resource");
        resource.setExternalId("EXT123");

        resourceDTO = new ResourceDTO();
        resourceDTO.setId(1);
        resourceDTO.setName("Sample Resource");
        resourceDTO.setExternalId("EXT123");
    }

    @Test
    @DisplayName("should return resources matching 'name' and 'externalId'")
    void testHandle_shouldReturnMatchingResources_whenFiltersProvided() {
        // Arrange
        GetResourcesQuery query = new GetResourcesQuery();
        query.setName("Sample");
        query.setExternalID("EXT123");

        when(resourceRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(resource));
        when(resourceMapper.toDto(resource)).thenReturn(resourceDTO);

        // Act
        ResponseEntity<List<ResourceDTO>> response = handler.handle(query);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("Sample Resource", response.getBody().getFirst().getName());

        // Verify
        verify(resourceRepository, times(1)).findAll(any(Specification.class));
        verify(resourceMapper, times(1)).toDto(resource);
    }

    @Test
    @DisplayName("should return empty list when no resources match the criteria")
    void testHandle_shouldReturnEmptyList_whenNoMatches() {
        // Arrange
        GetResourcesQuery query = new GetResourcesQuery();
        query.setName("NonExistent");

        when(resourceRepository.findAll(any(Specification.class))).thenReturn(List.of());

        // Act
        ResponseEntity<List<ResourceDTO>> response = handler.handle(query);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());

        // Verify
        verify(resourceRepository, times(1)).findAll(any(Specification.class));
        verify(resourceMapper, never()).toDto(any());
    }

    @Test
    @DisplayName("should return all resources when filters are null")
    void testHandle_shouldReturnAllResources_whenFiltersNull() {
        // Arrange
        GetResourcesQuery query = new GetResourcesQuery();

        when(resourceRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(resource));
        when(resourceMapper.toDto(resource)).thenReturn(resourceDTO);

        // Act
        ResponseEntity<List<ResourceDTO>> response = handler.handle(query);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, Objects.requireNonNull(response.getBody()).size());

        // Verify
        verify(resourceRepository).findAll(any(Specification.class));
        verify(resourceMapper).toDto(resource);
    }
}
