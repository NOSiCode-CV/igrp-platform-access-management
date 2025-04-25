package cv.igrp.platform.access_management.resource.application.commands.handlers;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.resource.application.queries.handlers.GetResourceByIdQueryHandler;
import cv.igrp.platform.access_management.resource.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Permission;
import cv.igrp.platform.access_management.shared.domain.models.Resource;
import cv.igrp.platform.access_management.shared.domain.models.ResourceItem;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.PermissionRepository;
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

    @Mock
    private PermissionRepository permissionRepository;

    private ResourceMapper resourceMapper;

    @BeforeEach
    void setUp() {
        this.resourceMapper = new ResourceMapper();
        this.handler = new AddItemsCommandHandler(resourceRepository, resourceMapper, permissionRepository);
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

        Permission permission1 = new Permission();
        permission1.setId(10);
        Permission permission2 = new Permission();
        permission2.setId(20);

        when(resourceRepository.findById(resourceId)).thenReturn(Optional.of(resource));
        when(permissionRepository.findById(10)).thenReturn(Optional.of(permission1));
        when(permissionRepository.findById(20)).thenReturn(Optional.of(permission2));
        when(resourceRepository.save(any(Resource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ResponseEntity<ResourceDTO> response = handler.handle(command);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(resourceId, response.getBody().getId());
        assertEquals(2, resource.getItems().size());

        ResourceItemDTO itemR = response.getBody().getItems().getFirst();
        assertEquals("Dashboard", itemR.getName());
        assertEquals("/dashboard", itemR.getUrl());

        ResourceItem item2 = resource.getItems().get(1);
        assertEquals("Settings", item2.getName());
        assertEquals("/settings", item2.getUrl());

        assertEquals(1, itemR.getResourceId());
        assertEquals(10, itemR.getPermissionId());
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

    @Test
    void testHandle_ShouldThrowException_WhenPermissionNotFound() {
        // Given
        Integer resourceId = 1;
        Integer missingPermissionId = 999;

        AddItemsCommand command = new AddItemsCommand();
        command.setId(resourceId);

        ResourceItemDTO itemDTO = new ResourceItemDTO();
        itemDTO.setName("Restricted");
        itemDTO.setUrl("/restricted");
        itemDTO.setPermissionId(missingPermissionId);
        itemDTO.setResourceId(resourceId);

        command.setResourceitemdto(List.of(itemDTO));

        Resource resource = new Resource();
        resource.setId(resourceId);
        resource.setItems(new ArrayList<>());

        when(resourceRepository.findById(resourceId)).thenReturn(Optional.of(resource));
        when(permissionRepository.findById(missingPermissionId)).thenReturn(Optional.empty());

        // When / Then
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> handler.handle(command));

        assertEquals(HttpStatus.NOT_FOUND, ex.getProblem().getStatus());
        assertTrue(ex.getProblem().getTitle().contains("Permission not found"));
    }
}

