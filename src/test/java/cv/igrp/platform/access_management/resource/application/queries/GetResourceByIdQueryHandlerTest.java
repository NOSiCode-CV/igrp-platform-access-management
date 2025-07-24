package cv.igrp.platform.access_management.resource.application.queries;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import cv.igrp.platform.access_management.resource.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.application.constants.ResourceType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceEntity;
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
import cv.igrp.platform.access_management.resource.application.dto.*;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetResourceByIdQueryHandler Integration Test")
class GetResourceByIdQueryHandlerTest {

    @Mock
    private ResourceEntityRepository resourceRepository;

    @Mock
    private ResourceMapper resourceMapper;

    @InjectMocks
    private GetResourceByIdQueryHandler handler;

    private ResourceEntity resource;
    private ResourceDTO resourceDTO;

    @BeforeEach
    void setUp() {
        handler = new GetResourceByIdQueryHandler(resourceRepository, resourceMapper);

        resource = new ResourceEntity();
        resource.setId(1);
        resource.setName("Document API");
        resource.setType(ResourceType.API);
        resource.setStatus(Status.ACTIVE);
        resource.setExternalId("ext-doc-api");

        resourceDTO = new ResourceDTO();
        resourceDTO.setId(1);
        resourceDTO.setName("Document API");
        resourceDTO.setType(ResourceType.API);
        resourceDTO.setStatus(Status.ACTIVE);
        resourceDTO.setExternalId("ext-doc-api");

    }

    @Test
    @DisplayName("should return 200 OK with valid ResourceDTO when resource exists")
    void testHandle_whenResourceExists_shouldReturnDto() {
        // Arrange
        GetResourceByIdQuery query = new GetResourceByIdQuery(1);
        when(resourceRepository.findById(1)).thenReturn(Optional.of(resource));

        when(resourceMapper.toDto(resource)).thenReturn(resourceDTO);

        // Act
        ResponseEntity<ResourceDTO> response = handler.handle(query);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        ResourceDTO dto = response.getBody();
        assertNotNull(dto);
        assertEquals(resource.getId(), dto.getId());
        assertEquals(resource.getName(), dto.getName());
        assertEquals(resource.getType(), dto.getType());
        assertEquals(resource.getExternalId(), dto.getExternalId());

        // Verify
        verify(resourceRepository, times(1)).findById(1);
        verifyNoMoreInteractions(resourceRepository);
    }

    @Test
    @DisplayName("should throw IgrpResponseStatusException when resource does not exist")
    void testHandle_whenResourceNotFound_shouldThrowException() {
        // Arrange
        GetResourceByIdQuery query = new GetResourceByIdQuery(999);
        when(resourceRepository.findById(999)).thenReturn(Optional.empty());

        // Act
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class, () -> handler.handle(query));

        // Assert
        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getBody().getStatus());

        assertNotNull(ex.getBody().getProperties());
        assertTrue(ex.getBody().getProperties().getOrDefault("details", "").toString().contains("999"));

        // Verify
        verify(resourceRepository, times(1)).findById(999);
        verifyNoMoreInteractions(resourceRepository);
    }
}