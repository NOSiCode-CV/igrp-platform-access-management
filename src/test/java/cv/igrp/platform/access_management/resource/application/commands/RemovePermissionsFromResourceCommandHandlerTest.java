package cv.igrp.platform.access_management.resource.application.commands;

import cv.igrp.platform.access_management.resource.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.ResourceDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ResourceEntityRepository;
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
 * Unit tests for {@link RemovePermissionsFromResourceCommandHandler}.
 * Verifies successful permission removal, resource not found scenario, and repository interactions.
 */
@ExtendWith(MockitoExtension.class)
class RemovePermissionsFromResourceCommandHandlerTest {

    @Mock
    private ResourceEntityRepository resourceEntityRepository;

    @Mock
    private ResourceMapper resourceMapper;

    @InjectMocks
    private RemovePermissionsFromResourceCommandHandler handler;

    private ResourceEntity resourceEntity;
    private PermissionEntity permission2;

    @BeforeEach
    void setUp() {
        PermissionEntity permission1 = new PermissionEntity();
        permission1.setName("READ");

        permission2 = new PermissionEntity();
        permission2.setName("WRITE");

        resourceEntity = new ResourceEntity();
        resourceEntity.setName("MyResource");
        resourceEntity.setPermissions(new HashSet<>(Arrays.asList(permission1, permission2)));
    }

    @Test
    void testHandle_SuccessfullyRemovesPermissions() {
        // Given
        List<String> permissionsToRemove = Collections.singletonList("READ");
        RemovePermissionsFromResourceCommand command =
                new RemovePermissionsFromResourceCommand(permissionsToRemove, "MyResource");

        when(resourceEntityRepository.findByNameAndStatusNot("MyResource", Status.DELETED))
                .thenReturn(Optional.of(resourceEntity));

        ResourceEntity updatedResource = new ResourceEntity();
        updatedResource.setName("MyResource");
        updatedResource.setPermissions(Set.of(permission2)); // Only WRITE remains

        when(resourceEntityRepository.save(any(ResourceEntity.class))).thenReturn(updatedResource);

        ResourceDTO expectedDto = new ResourceDTO();
        expectedDto.setName("MyResource");
        when(resourceMapper.toDto(updatedResource)).thenReturn(expectedDto);

        // When
        ResponseEntity<ResourceDTO> response = handler.handle(command);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("MyResource", response.getBody().getName());
        verify(resourceEntityRepository, times(1)).findByNameAndStatusNot("MyResource", Status.DELETED);
        verify(resourceEntityRepository, times(1)).save(any(ResourceEntity.class));
        verify(resourceMapper, times(1)).toDto(any(ResourceEntity.class));
    }

    @Test
    void testHandle_ResourceNotFound_ShouldThrowException() {
        // Given
        List<String> permissionsToRemove = Collections.singletonList("DELETE");
        RemovePermissionsFromResourceCommand command =
                new RemovePermissionsFromResourceCommand(permissionsToRemove, "NonExistentResource");

        when(resourceEntityRepository.findByNameAndStatusNot("NonExistentResource", Status.DELETED))
                .thenReturn(Optional.empty());

        // When / Then
        IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class,
                () -> handler.handle(command));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertTrue(exception.getMessage().contains("Resource not found"));
        verify(resourceEntityRepository, times(1)).findByNameAndStatusNot("NonExistentResource", Status.DELETED);
        verify(resourceEntityRepository, never()).save(any());
        verify(resourceMapper, never()).toDto(any());
    }

    @Test
    void testHandle_RemovePermissionNotPresent_NoError() {
        // Given
        List<String> permissionsToRemove = Collections.singletonList("EXECUTE");
        RemovePermissionsFromResourceCommand command =
                new RemovePermissionsFromResourceCommand(permissionsToRemove, "MyResource");

        when(resourceEntityRepository.findByNameAndStatusNot("MyResource", Status.DELETED))
                .thenReturn(Optional.of(resourceEntity));

        when(resourceEntityRepository.save(any(ResourceEntity.class))).thenReturn(resourceEntity);
        ResourceDTO expectedDto = new ResourceDTO();
        expectedDto.setName("MyResource");
        when(resourceMapper.toDto(resourceEntity)).thenReturn(expectedDto);

        // When
        ResponseEntity<ResourceDTO> response = handler.handle(command);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("MyResource", response.getBody().getName());
        verify(resourceEntityRepository, times(1)).save(any(ResourceEntity.class));
    }
}