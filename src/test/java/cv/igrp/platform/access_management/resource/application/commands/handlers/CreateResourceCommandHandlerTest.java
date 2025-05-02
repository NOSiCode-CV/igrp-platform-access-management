package cv.igrp.platform.access_management.resource.application.commands.handlers;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.resource.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.application.constants.ResourceType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.domain.models.Permission;
import cv.igrp.platform.access_management.shared.domain.models.Resource;
import cv.igrp.platform.access_management.shared.domain.models.ResourceItem;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ApplicationRepository;
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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class CreateResourceCommandHandlerTest {

    private CreateResourceCommandHandler createResourceCommandHandler;

    @Mock
    private ResourceRepository resourceRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private PermissionRepository permissionRepository;

    private ResourceMapper resourceMapper;

    @BeforeEach
    void setUp() {
        resourceMapper = new ResourceMapper();
        this.createResourceCommandHandler = new CreateResourceCommandHandler(resourceRepository, applicationRepository, resourceMapper, permissionRepository);
    }

    @Test
    void testHandle_ShouldCreateResource_WhenApplicationExists() {
        // Given
        Integer applicationId = 1;
        Integer permissionId = 1;

        ResourceDTO resourceDTO = new ResourceDTO();
        resourceDTO.setApplicationId(applicationId);
        resourceDTO.setName("Test Resource");
        resourceDTO.setType(ResourceType.API);

        ResourceItemDTO resourceItemDTO = new ResourceItemDTO();
        resourceItemDTO.setName("Item1");
        resourceItemDTO.setUrl("url1");
        resourceItemDTO.setPermissionId(permissionId); // Set this to match the stub
        resourceDTO.setItems(List.of(resourceItemDTO));

        CreateResourceCommand command = new CreateResourceCommand();
        command.setResourcedto(resourceDTO);

        Application application = new Application();
        application.setId(applicationId);

        Permission permission = new Permission();
        permission.setId(permissionId);

        Resource savedResource = new Resource();
        savedResource.setId(1);
        savedResource.setName("Test Resource");
        savedResource.setType(ResourceType.API);
        savedResource.setStatus(Status.ACTIVE);
        savedResource.setApplicationId(application);

        ResourceItem resourceItem = new ResourceItem();
        resourceItem.setName("Item1");
        resourceItem.setUrl("url1");
        resourceItem.setPermissionId(permission);
        resourceItem.setResourceId(savedResource);
        savedResource.setItems(List.of(resourceItem));

        // Stubs
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(permissionRepository.findById(permissionId)).thenReturn(Optional.of(permission));
        when(resourceRepository.save(any(Resource.class))).thenReturn(savedResource);

        // When
        ResponseEntity<ResourceDTO> response = createResourceCommandHandler.handle(command);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Test Resource", response.getBody().getName());
        assertEquals(1, response.getBody().getId());

        ResourceItemDTO itemR = response.getBody().getItems().getFirst();
        assertEquals(1, itemR.getResourceId());
        assertEquals(1, itemR.getPermissionId());

        verify(applicationRepository, times(1)).findById(applicationId);
        verify(permissionRepository, times(1)).findById(permissionId);
        verify(resourceRepository, times(1)).save(any(Resource.class));
    }


    @Test
    void testHandle_ShouldThrowException_WhenApplicationNotFound() {
        // Given
        Integer applicationId = 1;
        ResourceDTO resourceDTO = new ResourceDTO();
        resourceDTO.setApplicationId(applicationId);
        CreateResourceCommand command = new CreateResourceCommand();
        command.setResourcedto(resourceDTO);

        when(applicationRepository.findById(applicationId)).thenReturn(Optional.empty());

        // When / Then
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> createResourceCommandHandler.handle(command));

        // Assert the exception message and status
        assertEquals(HttpStatus.NOT_FOUND, ex.getProblem().getStatus());
        assertTrue(ex.getProblem().getTitle().contains("Application not found"));
    }

    @Test
    void testHandle_ShouldThrowException_WhenPermissionNotFound() {
        // Given
        Integer applicationId = 1;
        Integer permissionId = 999;

        ResourceDTO resourceDTO = new ResourceDTO();
        resourceDTO.setApplicationId(applicationId);
        resourceDTO.setName("Test Resource");

        ResourceItemDTO resourceItemDTO = new ResourceItemDTO();
        resourceItemDTO.setName("Item1");
        resourceItemDTO.setUrl("url1");
        resourceItemDTO.setPermissionId(permissionId);

        resourceDTO.setItems(List.of(resourceItemDTO));

        CreateResourceCommand command = new CreateResourceCommand();
        command.setResourcedto(resourceDTO);

        Application application = new Application();
        application.setId(applicationId);

        // Stubs
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(permissionRepository.findById(permissionId)).thenReturn(Optional.empty());

        // When / Then
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> createResourceCommandHandler.handle(command));

        assertEquals(HttpStatus.NOT_FOUND, ex.getProblem().getStatus());
        assertTrue(ex.getProblem().getTitle().contains("Permission not found"));

        // Verifications
        verify(applicationRepository, times(1)).findById(applicationId);
        verify(permissionRepository, times(1)).findById(permissionId);
        verify(resourceRepository, never()).save(any());
    }

}
