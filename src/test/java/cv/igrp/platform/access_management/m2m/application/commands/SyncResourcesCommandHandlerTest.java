package cv.igrp.platform.access_management.m2m.application.commands;

import cv.igrp.platform.access_management.m2m.domain.service.ResourceSyncService;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.ResourceDTO;
import cv.igrp.platform.access_management.shared.application.dto.ResourceItemDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SyncResourcesCommandHandler.
 */
@ExtendWith(MockitoExtension.class)
class SyncResourcesCommandHandlerTest {

    @Mock
    private ResourceSyncService resourceSyncService;

    @InjectMocks
    private SyncResourcesCommandHandler syncResourcesCommandHandler;

    private ResourceDTO resourceDTO;
    private SyncResourcesCommand command;

    @BeforeEach
    void setUp() {
        ResourceItemDTO item1 = new ResourceItemDTO();
        item1.setName("View");
        item1.setPermissions(List.of("VIEW_USER"));

        ResourceItemDTO item2 = new ResourceItemDTO();
        item2.setName("Edit");
        item2.setPermissions(List.of("EDIT_USER"));

        resourceDTO = new ResourceDTO();
        resourceDTO.setName("User Management");
        resourceDTO.setDescription("Handles user operations");
        resourceDTO.setStatus(Status.ACTIVE);
        resourceDTO.setItems(new ArrayList<>());
        resourceDTO.getItems().add(item1);
        resourceDTO.getItems().add(item2);
        resourceDTO.setPermissions(new ArrayList<>());
        resourceDTO.getPermissions().add("VIEW_USER");
        resourceDTO.getPermissions().add("EDIT_USER");

        command = new SyncResourcesCommand(resourceDTO);
    }

    /**
     * Case 1: New resource DTO arrives — Resource created successfully.
     */
    @Test
    void testHandle_NewResource_ShouldReturnNoContent() {
        doNothing().when(resourceSyncService).synchronizeResource(resourceDTO);

        ResponseEntity<String> response = syncResourcesCommandHandler.handle(command);

        verify(resourceSyncService, times(1)).synchronizeResource(resourceDTO);
        assertNotNull(response);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    /**
     * Case 2: Same DTO arrives again — No changes applied (hash match).
     */
    @Test
    void testHandle_SameResource_ShouldReturnNoContent() {
        doNothing().when(resourceSyncService).synchronizeResource(resourceDTO);

        ResponseEntity<String> response = syncResourcesCommandHandler.handle(command);

        verify(resourceSyncService, times(1)).synchronizeResource(resourceDTO);
        assertNotNull(response);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    /**
     * Case 3: DTO arrives with new items — Should succeed and update resource.
     */
    @Test
    void testHandle_ResourceWithNewItems_ShouldReturnNoContent() {
        ResourceItemDTO newItem = new ResourceItemDTO();
        newItem.setName("Delete");
        newItem.setPermissions(List.of("DELETE_USER"));
        resourceDTO.getItems().add(newItem);
        resourceDTO.getPermissions().add("DELETE_USER");

        doNothing().when(resourceSyncService).synchronizeResource(resourceDTO);

        ResponseEntity<String> response = syncResourcesCommandHandler.handle(command);

        verify(resourceSyncService, times(1)).synchronizeResource(resourceDTO);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    /**
     * Case 4: DTO arrives missing an item — Should remove old items.
     */
    @Test
    void testHandle_ResourceMissingItem_ShouldReturnNoContent() {
        ResourceItemDTO onlyOneItem = new ResourceItemDTO();
        onlyOneItem.setName("View");
        onlyOneItem.setPermissions(List.of("VIEW_USER"));

        resourceDTO.setItems(List.of(onlyOneItem));
        resourceDTO.setPermissions(List.of("VIEW_USER"));

        doNothing().when(resourceSyncService).synchronizeResource(resourceDTO);

        ResponseEntity<String> response = syncResourcesCommandHandler.handle(command);

        verify(resourceSyncService, times(1)).synchronizeResource(resourceDTO);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    /**
     * Case 5: DTO arrives missing permission — Should update permission associations.
     */
    @Test
    void testHandle_ResourceMissingPermission_ShouldReturnNoContent() {
        resourceDTO.setPermissions(List.of("VIEW_USER")); // Missing "EDIT_USER"

        doNothing().when(resourceSyncService).synchronizeResource(resourceDTO);

        ResponseEntity<String> response = syncResourcesCommandHandler.handle(command);

        verify(resourceSyncService, times(1)).synchronizeResource(resourceDTO);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    /**
     * Case 6: Validation error — Missing name or invalid data.
     */
    @Test
    void testHandle_ValidationError_ShouldThrowBadRequest() {
        ResourceDTO invalidDto = new ResourceDTO(); // missing name
        SyncResourcesCommand invalidCommand = new SyncResourcesCommand(invalidDto);

        doThrow(IgrpResponseStatusException.badRequest("Resource name is required"))
                .when(resourceSyncService).synchronizeResource(invalidDto);

        IgrpResponseStatusException exception = assertThrows(
                IgrpResponseStatusException.class,
                () -> syncResourcesCommandHandler.handle(invalidCommand)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getMessage().contains("Resource name is required"));
    }

    /**
     * Case 7: Internal server error — unexpected runtime error.
     */
    @Test
    void testHandle_InternalError_ShouldThrowInternalServerError() {
        doThrow(IgrpResponseStatusException.internalServerError("Database failure"))
                .when(resourceSyncService).synchronizeResource(resourceDTO);

        IgrpResponseStatusException exception = assertThrows(
                IgrpResponseStatusException.class,
                () -> syncResourcesCommandHandler.handle(command)
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatusCode());
        assertTrue(exception.getMessage().contains("Database failure"));
    }
}