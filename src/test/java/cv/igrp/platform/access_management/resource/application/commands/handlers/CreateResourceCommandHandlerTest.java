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

import java.util.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateResourceCommandHandler Unit Tests")
public class CreateResourceCommandHandlerTest {

    @Mock
    private ResourceRepository resourceRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private ResourceMapper resourceMapper;

    @InjectMocks
    private CreateResourceCommandHandler handler;

    private ResourceDTO resourceDTO;
    private Resource resource;
    private Application application;
    private Permission permission;
    private ResourceItemDTO itemDTO;
    private ResourceItem resourceItem;
    private CreateResourceCommand command;

    private CreateResourceCommand createResourceCommand(ResourceDTO resourceDTO) {
        return new CreateResourceCommand(resourceDTO);
    }

    @BeforeEach
    void setUp() {
        resourceDTO = new ResourceDTO();
        resourceDTO.setApplicationId(1);

        itemDTO = new ResourceItemDTO();
        itemDTO.setPermissionId(10);
        itemDTO.setName("Item1");
        itemDTO.setUrl("url1");

        resourceDTO.setId(1);
        resourceDTO.setItems(List.of(itemDTO));
        resourceDTO.setName("Test Resource");
        resourceDTO.setType(ResourceType.API);

        resource = new Resource();
        resource.setStatus(Status.ACTIVE);

        application = new Application();
        application.setId(1);

        permission = new Permission();
        permission.setId(10);

        resourceItem = new ResourceItem();
    }

    @Test
    @DisplayName("should create and return resource when input is valid")
    void testHandle_whenValidInput_shouldCreateResource() {
        // Arrange
        command = createResourceCommand(resourceDTO);
        when(resourceMapper.toEntity(resourceDTO)).thenReturn(resource);
        when(applicationRepository.findById(1)).thenReturn(Optional.of(application));
        when(permissionRepository.findById(10)).thenReturn(Optional.of(permission));
        when(resourceMapper.toItemEntity(itemDTO, resource, permission)).thenReturn(resourceItem);
        when(resourceRepository.save(resource)).thenReturn(resource);
        when(resourceMapper.toDto(resource)).thenReturn(resourceDTO);

        // Act
        ResponseEntity<ResourceDTO> response = handler.handle(command);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(resourceDTO, response.getBody());
        assertEquals("Test Resource", Objects.requireNonNull(response.getBody()).getName());
        assertEquals(1, response.getBody().getId());

        // Verify
        verify(resourceMapper).toEntity(resourceDTO);
        verify(applicationRepository,times(1)).findById(1);
        verify(permissionRepository, times(1)).findById(10);
        verify(resourceMapper, times(1)).toItemEntity(itemDTO, resource, permission);
        verify(resourceRepository, times(1)).save(resource);
        verify(resourceMapper, times(1)).toDto(resource);
    }

    @Test
    @DisplayName("should throw IgrpResponseStatusException if application not found")
    void testHandle_whenApplicationNotFound_shouldThrow() {
        // Arrange
        command = createResourceCommand(resourceDTO);
        when(resourceMapper.toEntity(resourceDTO)).thenReturn(resource);
        when(applicationRepository.findById(1)).thenReturn(Optional.empty());

        // Act
        IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class, () -> handler.handle(command));

        // Assert
        assertEquals(HttpStatus.NOT_FOUND.value(), exception.getBody().getStatus());
        assertNotNull(exception.getBody().getDetail());
        assertTrue(exception.getBody().getDetail().contains("Application not found with id: 1"));

        // Verify
        verify(resourceMapper, times(1)).toEntity(resourceDTO);
        verify(applicationRepository, times(1)).findById(1);
        verifyNoMoreInteractions(permissionRepository, resourceRepository);
    }

    @Test
    @DisplayName("should throw IgrpResponseStatusException if permission not found")
    void testHandle_whenPermissionNotFound_shouldThrow() {
        // Arrange
        command = createResourceCommand(resourceDTO);
        when(resourceMapper.toEntity(resourceDTO)).thenReturn(resource);
        when(applicationRepository.findById(1)).thenReturn(Optional.of(application));
        when(permissionRepository.findById(10)).thenReturn(Optional.empty());

        // Act
        IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class, () -> handler.handle(command));

        // Assert
        assertEquals(HttpStatus.NOT_FOUND.value(), exception.getBody().getStatus());
        assertNotNull(exception.getBody().getDetail());
        assertTrue(exception.getBody().getDetail().contains("Permission not found with id: 10"));

        // Verify
        verify(resourceMapper, times(1)).toEntity(resourceDTO);
        verify(applicationRepository, times(1)).findById(1);
        verify(permissionRepository, times(1)).findById(10);
        verifyNoMoreInteractions(permissionRepository, resourceRepository, resourceMapper);
    }

    @Test
    @DisplayName("should not throw if items list is empty")
    void testHandle_whenItemsListIsEmpty_shouldCreateResource() {
        // Arrange
        resourceDTO.setItems(Collections.emptyList());
        command = createResourceCommand(resourceDTO);
        when(resourceMapper.toEntity(resourceDTO)).thenReturn(resource);
        when(applicationRepository.findById(1)).thenReturn(Optional.of(application));
        when(resourceRepository.save(resource)).thenReturn(resource);
        when(resourceMapper.toDto(resource)).thenReturn(resourceDTO);

        // Act
        ResponseEntity<ResourceDTO> response = handler.handle(command);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(resourceDTO, response.getBody());

        // Verify
        verify(applicationRepository, times(1)).findById(1);
        verify(resourceRepository, times(1)).save(resource);
        verifyNoInteractions(permissionRepository);
    }
}
