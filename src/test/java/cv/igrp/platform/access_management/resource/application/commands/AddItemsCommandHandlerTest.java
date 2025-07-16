package cv.igrp.platform.access_management.resource.application.commands;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import cv.igrp.platform.access_management.resource.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceItemEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
@DisplayName("AddItemsCommandHandler Tests")
class AddItemsCommandHandlerTest {

    @Mock
    private ResourceEntityRepository resourceRepository;

    @Mock
    private PermissionEntityRepository permissionRepository;

    @Mock
    private ResourceMapper resourceMapper;

    @InjectMocks
    private AddItemsCommandHandler handler;

    private AddItemsCommand command;

    private AddItemsCommand addItemsCommand(List<ResourceItemDTO> resourceItemDTOS) {
        return new AddItemsCommand(resourceItemDTOS, 1);
    }

    private ResourceEntity resource;
    private PermissionEntity permission;
    private ResourceItemDTO itemDTO;
    private ResourceItemEntity item;
    private ResourceDTO resourceDTO;

    @BeforeEach
    void setUp() {
        resource = new ResourceEntity();
        permission = new PermissionEntity();
        permission.setId(1);
        itemDTO = new ResourceItemDTO();
        itemDTO.setPermissionId(1);
        itemDTO.setName("Dashboard");
        itemDTO.setUrl("/dashboard");
        item = new ResourceItemEntity();
        resourceDTO = new ResourceDTO();
    }

    @Test
    @DisplayName("should add items successfully")
    void testHandle_shouldAddItemsSuccessfully() {
        // Arrange
        resource.setItems(new ArrayList<>());
        resourceDTO.setItems(List.of(itemDTO));
        when(resourceRepository.findById(1)).thenReturn(Optional.of(resource));
        when(permissionRepository.findById(1)).thenReturn(Optional.of(permission));
        when(resourceMapper.toItemEntity(itemDTO, resource, permission)).thenReturn(item);
        when(resourceRepository.save(resource)).thenReturn(resource);
        when(resourceMapper.toDto(resource)).thenReturn(resourceDTO);


        // Act
        command = addItemsCommand(List.of(itemDTO));
        ResponseEntity<ResourceDTO> response = handler.handle(command);

        // Assert
        assertNotNull(response.getBody());
        assertEquals(resourceDTO, response.getBody());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(itemDTO, response.getBody().getItems().getFirst());

        // Verify
        verify(resourceRepository, times(1)).findById(1);
        verify(resourceRepository, times(1)).save(resource);
        verify(resourceMapper,times(1)).toDto(resource);
        verify(resourceMapper, times(1)).toItemEntity(itemDTO, resource, permission);
        verifyNoMoreInteractions(resourceRepository, permissionRepository, resourceMapper);
    }

    @Test
    @DisplayName("should initialize items list if null")
    void testHandle_whenResourceItemsListIsNull_shouldInitializeList() {
        // Arrange
        resource.setItems(null);
        when(resourceRepository.findById(1)).thenReturn(Optional.of(resource));
        when(permissionRepository.findById(1)).thenReturn(Optional.of(permission));
        when(resourceMapper.toItemEntity(itemDTO, resource, permission)).thenReturn(item);
        when(resourceRepository.save(resource)).thenReturn(resource);
        when(resourceMapper.toDto(resource)).thenReturn(resourceDTO);

        // Act
        AddItemsCommand command = new AddItemsCommand(List.of(itemDTO), 1);
        ResponseEntity<ResourceDTO> response = handler.handle(command);

        // Assert
        assertNotNull(response);
        assertEquals(resourceDTO, response.getBody());
        assertNotNull(resource.getItems());

        // Verify
        verify(resourceRepository, times(1)).findById(1);
        verify(resourceRepository, times(1)).save(resource);
        verifyNoMoreInteractions(permissionRepository, resourceMapper, resourceRepository);
    }

    @Test
    @DisplayName("should throw IgrpResponseStatusException when resource not found")
    void testHandle_shouldThrow_whenResourceNotFound() {
        // Arrange
        when(resourceRepository.findById(1)).thenReturn(Optional.empty());

        // Act
        AddItemsCommand command = new AddItemsCommand(List.of(itemDTO), 1);

        // Assert
        assertThrows(IgrpResponseStatusException.class, () -> handler.handle(command));

        // Verify
        verify(resourceRepository, times(1)).findById(1);
        verifyNoInteractions(permissionRepository, resourceMapper);
    }

    @Test
    @DisplayName("should throw IgrpResponseStatusException when permission not found")
    void testHandle_shouldThrow_whenPermissionNotFound() {
        // Arrange
        when(resourceRepository.findById(1)).thenReturn(Optional.of(resource));
        when(permissionRepository.findById(1)).thenReturn(Optional.empty());

        // Act
        AddItemsCommand command = new AddItemsCommand(List.of(itemDTO), 1);

        // Assert
        assertThrows(IgrpResponseStatusException.class, () -> handler.handle(command));

        // Verify
        verify(permissionRepository).findById(1);
        verifyNoMoreInteractions(permissionRepository);
    }

    @Test
    @DisplayName("should succeed with empty item list")
    void testHandle_shouldWorkWithEmptyItemList() {
        // Arrange
        when(resourceRepository.findById(1)).thenReturn(Optional.of(resource));
        when(resourceRepository.save(resource)).thenReturn(resource);
        when(resourceMapper.toDto(resource)).thenReturn(resourceDTO);

        // Act
        AddItemsCommand command = new AddItemsCommand(Collections.emptyList(), 1);
        ResponseEntity<ResourceDTO> response = handler.handle(command);

        // Assert
        assertNotNull(response);
        assertEquals(resourceDTO, response.getBody());

        // Verify
        verify(resourceRepository).save(resource);
    }

    @Test
    @DisplayName("should fail if command ID is null (validation fallback)")
    void testHandle_whenCommandIdIsNull_shouldThrow() {
        // Act
        AddItemsCommand command = new AddItemsCommand(List.of(itemDTO), null);

        // Assert
        assertThrows(IgrpResponseStatusException.class, () -> handler.handle(command));
    }

}

