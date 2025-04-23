package cv.igrp.platform.access_management.resource.application.queries.handlers;


import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.resource.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.application.constants.ResourceType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.domain.models.Resource;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ResourceRepository;
import org.junit.jupiter.api.BeforeEach;
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
import cv.igrp.platform.access_management.resource.application.queries.handlers.*;

import java.util.List;

@ExtendWith(MockitoExtension.class)
public class GetResourcesQueryHandlerTest {

    @InjectMocks
    private GetResourcesQueryHandler getResourcesQueryHandler;

    @Mock
    private ResourceRepository resourceRepository;

    private ResourceMapper resourceMapper;

    @BeforeEach
    void setUp() {
        resourceMapper = new ResourceMapper();
        getResourcesQueryHandler = new GetResourcesQueryHandler(resourceRepository, resourceMapper);
    }

    @Test
    void testHandleGetResourcesQuery() {
        // Given
        GetResourcesQuery query = new GetResourcesQuery(1, "TestResource", "API", "ext123");

        Resource resource = new Resource();
        resource.setId(1);
        resource.setName("TestResource");
        resource.setType(ResourceType.API);
        resource.setStatus(Status.ACTIVE);
        resource.setExternalId("ext123");

        Application app = new Application();
        app.setId(1);
        resource.setApplicationId(app);

        List<Resource> resourceList = List.of(resource);
        when(resourceRepository.findAll(any(Specification.class))).thenReturn(resourceList);

        // When
        ResponseEntity<List<ResourceDTO>> response = getResourcesQueryHandler.handle(query);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());

        ResourceDTO result = response.getBody().get(0);
        assertEquals("TestResource", result.getName());
        assertEquals(ResourceType.API, result.getType());
        assertEquals("ext123", result.getExternalId());
        assertEquals(1, result.getApplicationId());
    }
}
