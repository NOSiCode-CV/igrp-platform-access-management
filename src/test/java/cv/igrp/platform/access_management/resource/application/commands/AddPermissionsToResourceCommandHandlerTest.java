package cv.igrp.platform.access_management.resource.application.commands;

import cv.igrp.platform.access_management.resource.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.ResourceDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ResourceEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AddPermissionsToResourceCommandHandler}.
 * Tests the logic for adding permissions to a resource and handling edge cases.
 */
@ExtendWith(MockitoExtension.class)
class AddPermissionsToResourceCommandHandlerTest {

    @Mock
    private PermissionEntityRepository permissionEntityRepository;

    @Mock
    private ResourceEntityRepository resourceEntityRepository;

    @Mock
    private ResourceMapper resourceMapper;

    @InjectMocks
    private AddPermissionsToResourceCommandHandler handler;

    private AddPermissionsToResourceCommand command;
    private PermissionEntity permission1;
    private PermissionEntity permission2;
    private ResourceEntity resourceEntity;
    private ResourceDTO resourceDTO;

    @BeforeEach
    void setUp() {
        command = new AddPermissionsToResourceCommand();
        command.setName("TestResource");
        command.setAddPermissionsToResourceRequest(List.of("READ", "WRITE"));

        permission1 = new PermissionEntity();
        permission1.setName("READ");
        permission1.setStatus(Status.ACTIVE);

        permission2 = new PermissionEntity();
        permission2.setName("WRITE");
        permission2.setStatus(Status.ACTIVE);

        resourceEntity = new ResourceEntity();
        resourceEntity.setName("TestResource");
        resourceEntity.setStatus(Status.ACTIVE);
        resourceEntity.setPermissions(new HashSet<>());

        resourceDTO = new ResourceDTO();
        resourceDTO.setName("TestResource");
    }

    @Test
    void testHandle_ShouldAddPermissionsAndReturnUpdatedResource() {
        when(permissionEntityRepository.findAllByNameIn(List.of("READ", "WRITE")))
                .thenReturn(List.of(permission1, permission2));

        when(resourceEntityRepository.findByNameAndStatusNot("TestResource", Status.DELETED))
                .thenReturn(Optional.of(resourceEntity));

        when(resourceEntityRepository.save(any(ResourceEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(resourceMapper.toDto(any(ResourceEntity.class))).thenReturn(resourceDTO);

        ResponseEntity<ResourceDTO> response = handler.handle(command);

        assertNotNull(response);
        assertEquals(resourceDTO, response.getBody());
        verify(permissionEntityRepository).findAllByNameIn(anyList());
        verify(resourceEntityRepository).findByNameAndStatusNot(eq("TestResource"), eq(Status.DELETED));
        verify(resourceEntityRepository).save(any(ResourceEntity.class));
        verify(resourceMapper).toDto(any(ResourceEntity.class));
    }

    @Test
    void testHandle_ShouldThrow_WhenNoValidPermissionsFound() {
        when(permissionEntityRepository.findAllByNameIn(List.of("READ", "WRITE")))
                .thenReturn(List.of());

        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class, () -> handler.handle(command));

        assertEquals("Permission not found", ex.getBody().getTitle());
        verify(permissionEntityRepository).findAllByNameIn(anyList());
        verifyNoMoreInteractions(resourceEntityRepository);
    }

    @Test
    void testHandle_ShouldThrow_WhenResourceNotFound() {
        when(permissionEntityRepository.findAllByNameIn(List.of("READ", "WRITE")))
                .thenReturn(List.of(permission1, permission2));

        when(resourceEntityRepository.findByNameAndStatusNot("TestResource", Status.DELETED))
                .thenReturn(Optional.empty());

        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class, () -> handler.handle(command));

        assertEquals("Resource not found", ex.getBody().getTitle());
        verify(resourceEntityRepository).findByNameAndStatusNot(eq("TestResource"), eq(Status.DELETED));
        verify(resourceEntityRepository, never()).save(any());
    }
}