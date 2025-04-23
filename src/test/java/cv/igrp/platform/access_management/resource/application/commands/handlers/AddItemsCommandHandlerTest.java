package cv.igrp.platform.access_management.resource.application.commands.handlers;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.resource.application.queries.handlers.GetResourceByIdQueryHandler;
import cv.igrp.platform.access_management.resource.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Resource;
import cv.igrp.platform.access_management.shared.domain.models.ResourceItem;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import cv.igrp.platform.access_management.resource.application.commands.commands.*;
import cv.igrp.platform.access_management.resource.application.commands.handlers.*;
import cv.igrp.platform.access_management.resource.application.dto.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class AddItemsCommandHandlerTest {

    private AddItemsCommandHandler handler;

    @Mock
    private ResourceRepository resourceRepository;

    private ResourceMapper resourceMapper;

    @BeforeEach
    void setUp() {
        this.resourceMapper = new ResourceMapper();
        this.handler = new AddItemsCommandHandler(resourceRepository, resourceMapper);
    }


    @Test
    void testHandle_ShouldAddItemsAndReturnResource_WhenResourceExists() {
        // Given
        Integer resourceId = 1;

        AddItemsCommand command = new AddItemsCommand();
        command.setId(resourceId);

        ResourceItemDTO dto1 = new ResourceItemDTO();
        dto1.setName("Dashboard");
        dto1.setUrl("/dashboard");
        dto1.setPermissionId(10);
        dto1.setResourceId(resourceId);

        ResourceItemDTO dto2 = new ResourceItemDTO();
        dto2.setName("Settings");
        dto2.setUrl("/settings");
        dto2.setPermissionId(20);
        dto2.setResourceId(resourceId);

        command.setResourceitemdto(List.of(dto1, dto2));

        Resource resource = new Resource();
        resource.setId(resourceId);
        resource.setItems(new ArrayList<>());

        when(resourceRepository.findById(resourceId)).thenReturn(Optional.of(resource));
        when(resourceRepository.save(any(Resource.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ResponseEntity<ResourceDTO> response = handler.handle(command);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(resourceId, response.getBody().getId());
        assertEquals(2, resource.getItems().size());

        ResourceItem item1 = resource.getItems().get(0);
        assertEquals("Dashboard", item1.getName());
        assertEquals("/dashboard", item1.getUrl());

        ResourceItem item2 = resource.getItems().get(1);
        assertEquals("Settings", item2.getName());
        assertEquals("/settings", item2.getUrl());
    }

    @Test
    void testHandle_ShouldThrowException_WhenResourceNotFound() {
        // Given
        AddItemsCommand command = new AddItemsCommand();
        command.setId(99);
        when(resourceRepository.findById(99)).thenReturn(Optional.empty());
        // When / Then
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> handler.handle(command));
        assertEquals(HttpStatus.NOT_FOUND, ex.getProblem().getStatus());
        assertTrue(ex.getProblem().getTitle().contains("Resource not found"));
    }

}
