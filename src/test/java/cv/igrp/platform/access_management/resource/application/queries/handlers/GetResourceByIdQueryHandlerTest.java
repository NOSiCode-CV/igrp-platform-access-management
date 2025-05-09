package cv.igrp.platform.access_management.resource.application.queries.handlers;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.resource.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.application.constants.ResourceType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.domain.models.Resource;
import cv.igrp.platform.access_management.shared.domain.models.ResourceItem;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import cv.igrp.platform.access_management.resource.application.dto.*;
import cv.igrp.platform.access_management.resource.application.queries.queries.*;

import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
@Disabled
public class GetResourceByIdQueryHandlerTest {

    private GetResourceByIdQueryHandler getResourceByIdQueryHandler;

    @Mock
    private ResourceRepository resourceRepository;

    private ResourceMapper resourceMapper;

    @BeforeEach
    void setUp() {
      this.resourceMapper = new ResourceMapper();
      this.getResourceByIdQueryHandler = new GetResourceByIdQueryHandler(resourceRepository, resourceMapper);
    }

    @Test
    void testHandleGetResourceByIdQuery_success() {
        // Given
        int resourceId = 1;

        Resource resource = new Resource();
        resource.setId(1);
        resource.setName("Test Resource");
        resource.setType(ResourceType.API);
        resource.setStatus(Status.ACTIVE);
        resource.setExternalId("EXT-123");
        Application application = new Application();
        application.setId(10);
        resource.setApplicationId(application);
        ResourceItem item = new ResourceItem();
        item.setId(1);
        item.setName("Item1");
        item.setUrl("/test");
        item.setResourceId(resource);
        resource.setItems(List.of(item));

        GetResourceByIdQuery query = new GetResourceByIdQuery(resourceId);

        when(resourceRepository.findById(resourceId)).thenReturn(Optional.of(resource));

        // When
        ResponseEntity<ResourceDTO> result = getResourceByIdQueryHandler.handle(query);

        // Then
        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals("Test Resource", result.getBody().getName());
        assertEquals(resourceId, result.getBody().getId());
        assertEquals("EXT-123", result.getBody().getExternalId());
        assertEquals(1, result.getBody().getItems().size());

        verify(resourceRepository, times(1)).findById(resourceId);
    }

    @Test
    void testHandleGetResourceByIdQuery_notFound() {
        // Given
        int invalidId = 999;
        GetResourceByIdQuery query = new GetResourceByIdQuery(invalidId);

        when(resourceRepository.findById(invalidId)).thenReturn(Optional.empty());

        // When + Then
        IgrpResponseStatusException exception = assertThrows(
                IgrpResponseStatusException.class,
                () -> getResourceByIdQueryHandler.handle(query)
        );

        assertEquals("Resource not found with id: 999", exception.getProblem().getDetails());
        assertEquals(HttpStatus.NOT_FOUND, exception.getProblem().getStatus());
    }

}