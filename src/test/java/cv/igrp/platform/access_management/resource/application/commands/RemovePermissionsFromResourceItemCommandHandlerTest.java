package cv.igrp.platform.access_management.resource.application.commands;

import cv.igrp.platform.access_management.resource.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.application.dto.ResourceItemDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceItemEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ResourceItemEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RemovePermissionsFromResourceItemCommandHandler}.
 * <p>
 * Validates correct removal of permissions, exception handling when the resource item is not found,
 * and behavior when attempting to remove a non-existent permission.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class RemovePermissionsFromResourceItemCommandHandlerTest {

    @Mock
    private ResourceItemEntityRepository resourceItemEntityRepository;

    @Mock
    private ResourceMapper resourceMapper;

    @InjectMocks
    private RemovePermissionsFromResourceItemCommandHandler handler;

    private ResourceItemEntity resourceItemEntity;
    private PermissionEntity permission2;

    @BeforeEach
    void setUp() {
        PermissionEntity permission1 = new PermissionEntity();
        permission1.setName("READ");

        permission2 = new PermissionEntity();
        permission2.setName("WRITE");

        resourceItemEntity = new ResourceItemEntity();
        resourceItemEntity.setName("ItemA");
        resourceItemEntity.setPermissions(new ArrayList<>());

        resourceItemEntity.getPermissions().add(permission1);
        resourceItemEntity.getPermissions().add(permission2);
    }

    @Test
    void testHandle_SuccessfullyRemovesPermission() {
        // Given
        List<String> permissionsToRemove = Collections.singletonList("READ");
        RemovePermissionsFromResourceItemCommand command =
                new RemovePermissionsFromResourceItemCommand(permissionsToRemove, "ItemA");

        when(resourceItemEntityRepository.findByName("ItemA")).thenReturn(Optional.of(resourceItemEntity));

        ResourceItemEntity updatedItem = new ResourceItemEntity();
        updatedItem.setName("ItemA");
        updatedItem.setPermissions(new ArrayList<>()); // READ removed, WRITE remains
        updatedItem.getPermissions().add(permission2);

        when(resourceItemEntityRepository.save(any(ResourceItemEntity.class))).thenReturn(updatedItem);

        ResourceItemDTO expectedDto = new ResourceItemDTO();
        expectedDto.setName("ItemA");
        when(resourceMapper.toItemDto(updatedItem)).thenReturn(expectedDto);

        // When
        ResponseEntity<ResourceItemDTO> response = handler.handle(command);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ItemA", response.getBody().getName());
        verify(resourceItemEntityRepository, times(1)).findByName("ItemA");
        verify(resourceItemEntityRepository, times(1)).save(any(ResourceItemEntity.class));
        verify(resourceMapper, times(1)).toItemDto(any(ResourceItemEntity.class));
    }

    @Test
    void testHandle_ResourceItemNotFound_ShouldThrowException() {
        // Given
        List<String> permissionsToRemove = Collections.singletonList("READ");
        RemovePermissionsFromResourceItemCommand command =
                new RemovePermissionsFromResourceItemCommand(permissionsToRemove, "UnknownItem");

        when(resourceItemEntityRepository.findByName("UnknownItem")).thenReturn(Optional.empty());

        // When / Then
        IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class,
                () -> handler.handle(command));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertTrue(exception.getMessage().contains("Resource item not found"));
        verify(resourceItemEntityRepository, times(1)).findByName("UnknownItem");
        verify(resourceItemEntityRepository, never()).save(any());
        verify(resourceMapper, never()).toItemDto(any());
    }

    @Test
    void testHandle_PermissionNotPresent_NoError() {
        // Given
        List<String> permissionsToRemove = Collections.singletonList("EXECUTE");
        RemovePermissionsFromResourceItemCommand command =
                new RemovePermissionsFromResourceItemCommand(permissionsToRemove, "ItemA");

        when(resourceItemEntityRepository.findByName("ItemA")).thenReturn(Optional.of(resourceItemEntity));
        when(resourceItemEntityRepository.save(any(ResourceItemEntity.class))).thenReturn(resourceItemEntity);

        ResourceItemDTO expectedDto = new ResourceItemDTO();
        expectedDto.setName("ItemA");
        when(resourceMapper.toItemDto(resourceItemEntity)).thenReturn(expectedDto);

        // When
        ResponseEntity<ResourceItemDTO> response = handler.handle(command);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ItemA", response.getBody().getName());
        verify(resourceItemEntityRepository, times(1)).save(any(ResourceItemEntity.class));
    }
}
