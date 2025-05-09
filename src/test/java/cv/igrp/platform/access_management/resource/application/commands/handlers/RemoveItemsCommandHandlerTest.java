package cv.igrp.platform.access_management.resource.application.commands.handlers;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.resource.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
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
import cv.igrp.platform.access_management.resource.application.commands.commands.*;
import cv.igrp.platform.access_management.resource.application.dto.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
@Disabled
public class RemoveItemsCommandHandlerTest {

    private RemoveItemsCommandHandler removeItemsCommandHandler;

    @Mock
    private ResourceRepository resourceRepository;

    private ResourceMapper resourceMapper;

    @BeforeEach
    void setUp() {
        resourceMapper = new ResourceMapper();
        removeItemsCommandHandler = new RemoveItemsCommandHandler(resourceRepository, resourceMapper);
    }

    @Test
    void testHandle_ShouldRemoveItemsAndReturnResource_WhenResourceExists() {
        // Given
        Integer resourceId = 1;
        RemoveItemsCommand command = new RemoveItemsCommand();
        command.setId(resourceId);

        List<Integer> itemsToRemove = List.of(2, 3);
        command.setRemoveItemsRequest(itemsToRemove);

        // Create resource with items
        Resource resource = new Resource();
        resource.setId(resourceId);
        resource.setItems(new ArrayList<>());

        // Add some items to resource
        ResourceItem item1 = new ResourceItem();
        item1.setId(1);
        item1.setName("Dashboard");
        item1.setResourceId(resource);
        resource.getItems().add(item1);

        ResourceItem item2 = new ResourceItem();
        item2.setId(2);
        item2.setName("Settings");
        item2.setResourceId(resource);
        resource.getItems().add(item2);

        ResourceItem item3 = new ResourceItem();
        item3.setId(3);
        item3.setName("Reports");
        item3.setResourceId(resource);
        resource.getItems().add(item3);

        when(resourceRepository.findById(resourceId)).thenReturn(Optional.of(resource));
        when(resourceRepository.save(any(Resource.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ResponseEntity<ResourceDTO> response = removeItemsCommandHandler.handle(command);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(resourceId, response.getBody().getId());

        assertEquals(1, resource.getItems().size());
        assertEquals("Dashboard", resource.getItems().get(0).getName());
    }

    @Test
    void testHandle_ShouldThrowException_WhenResourceNotFound() {
        // Given
        RemoveItemsCommand command = new RemoveItemsCommand();
        command.setId(99);

        when(resourceRepository.findById(99)).thenReturn(Optional.empty());

        // When / Then
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> removeItemsCommandHandler.handle(command));

        assertEquals(HttpStatus.NOT_FOUND, ex.getProblem().getStatus());
        assertTrue(ex.getProblem().getTitle().contains("Resource not found"));
    }
}
