package cv.igrp.platform.access_management.resource.application.commands.handlers;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.resource.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Resource;
import cv.igrp.platform.access_management.shared.domain.models.ResourceItem;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
@DisplayName("RemoveItemsCommandHandler Unit Tests")
public class RemoveItemsCommandHandlerTest {

    @Mock
    private ResourceRepository resourceRepository;

    @Mock
    private ResourceMapper resourceMapper;

    @InjectMocks
    private RemoveItemsCommandHandler handler;

    private RemoveItemsCommand removeItemsCommand(List<Integer> itemsToRemove, Integer resourceId) {
        return new RemoveItemsCommand(itemsToRemove, resourceId);
    }

    private Resource resource;
    private ResourceDTO resourceDTO;
    private RemoveItemsCommand command;

    @BeforeEach
    void setUp() {
        resource = new Resource();
        resource.setId(1);
        resource.setItems(new ArrayList<>());

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
        resourceDTO = new ResourceDTO();
    }

    @Test
    @DisplayName("should return 200 OK without modifying when item list is null")
    void testHandle_whenItemListIsNull_shouldSkipRemoval() {
        // Arrange
        command = removeItemsCommand(null, 1);
        when(resourceRepository.findById(1)).thenReturn(Optional.of(resource));
        when(resourceMapper.toDto(resource)).thenReturn(resourceDTO);

        // Act
        ResponseEntity<ResourceDTO> response = handler.handle(command);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(resourceDTO, response.getBody());

        // Verify
        verify(resourceRepository, times(1)).findById(1);
        verify(resourceRepository, never()).save(any());
        verifyNoMoreInteractions(resourceRepository, resourceMapper);
    }

    @Test
    @DisplayName("should return 200 OK without modifying when item list is empty")
    void testHandle_whenItemListIsEmpty_shouldSkipRemoval() {
        // Arrange
        command = removeItemsCommand(new ArrayList<>(), 1);
        when(resourceRepository.findById(1)).thenReturn(Optional.of(resource));
        when(resourceMapper.toDto(resource)).thenReturn(resourceDTO);

        // Act
        ResponseEntity<ResourceDTO> response = handler.handle(command);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());

        // Verify
        verify(resourceRepository, times(1)).findById(1);
        verify(resourceRepository, never()).save(any());
        verifyNoMoreInteractions(resourceRepository, resourceMapper);
    }

    @Test
    @DisplayName("should remove matching items and return 200 OK")
    void testHandle_whenValidItemsProvided_shouldRemoveItems() {
        // Arrange

        command = removeItemsCommand(List.of(2), 1);
        when(resourceRepository.findById(1)).thenReturn(Optional.of(resource));
        when(resourceRepository.save(resource)).thenReturn(resource);
        when(resourceMapper.toDto(resource)).thenReturn(resourceDTO);

        // Act
        ResponseEntity<ResourceDTO> response = handler.handle(command);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(resourceDTO, response.getBody());
        assertEquals(2, resource.getItems().size());
        assertEquals(1, resource.getItems().get(0).getId());

        // Verify
        verify(resourceRepository, times(1)).findById(1);
        verify(resourceRepository, times(1)).save(resource);
        verify(resourceMapper, times(1)).toDto(resource);
        verifyNoMoreInteractions(resourceRepository, resourceMapper);
    }

    @Test
    @DisplayName("should throw IgrpResponseStatusException when resource not found")
    void handle_whenResourceNotFound_shouldThrow() {
        // Arrange
        command = removeItemsCommand( List.of(1), 999);
        when(resourceRepository.findById(999)).thenReturn(Optional.empty());

        // Act
        IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class, () -> handler.handle(command));

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, exception.getProblem().getStatus());

        // Verify
        verify(resourceRepository, times(1)).findById(999);
        verifyNoMoreInteractions(resourceRepository, resourceMapper);
    }
}
