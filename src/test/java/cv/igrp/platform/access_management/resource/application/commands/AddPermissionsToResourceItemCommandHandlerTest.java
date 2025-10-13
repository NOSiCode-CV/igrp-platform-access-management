package cv.igrp.platform.access_management.resource.application.commands;

import cv.igrp.platform.access_management.resource.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.ResourceItemDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceItemEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ResourceItemEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AddPermissionsToResourceItemCommandHandler}.
 * Verifies correct permission filtering, mapping, and exception behavior.
 */
@ExtendWith(MockitoExtension.class)
class AddPermissionsToResourceItemCommandHandlerTest {

    @Mock
    private PermissionEntityRepository permissionEntityRepository;

    @Mock
    private ResourceItemEntityRepository resourceItemEntityRepository;

    @Mock
    private ResourceMapper resourceMapper;

    @InjectMocks
    private AddPermissionsToResourceItemCommandHandler handler;

    private AddPermissionsToResourceItemCommand command;
    private ResourceItemEntity resourceItemEntity;
    private PermissionEntity permission1;
    private PermissionEntity permission2;
    private ResourceItemDTO resourceItemDTO;

    @BeforeEach
    void setUp() {
        command = new AddPermissionsToResourceItemCommand();
        command.setName("ItemA");
        command.setAddPermissionsToResourceItemRequest(List.of("READ", "WRITE"));

        permission1 = new PermissionEntity();
        permission1.setName("READ");
        permission1.setStatus(Status.ACTIVE);

        permission2 = new PermissionEntity();
        permission2.setName("WRITE");
        permission2.setStatus(Status.ACTIVE);

        ResourceEntity resourceEntity = new ResourceEntity();
        resourceEntity.setName("ResourceA");
        resourceEntity.setPermissions(new HashSet<>());

        resourceEntity.getPermissions().add(permission1);
        resourceEntity.getPermissions().add(permission2);

        resourceItemEntity = new ResourceItemEntity();
        resourceItemEntity.setName("ItemA");
        resourceItemEntity.setPermissions(new ArrayList<>());
        resourceItemEntity.setResourceId(resourceEntity);

        resourceItemDTO = new ResourceItemDTO();
        resourceItemDTO.setName("ItemA");
    }

    @Test
    void testHandle_ShouldAddPermissionsSuccessfully() {
        when(resourceItemEntityRepository.findByName("ItemA")).thenReturn(Optional.of(resourceItemEntity));
        when(permissionEntityRepository.findAllByNameIn(List.of("READ", "WRITE")))
                .thenReturn(List.of(permission1, permission2));
        when(resourceItemEntityRepository.save(any(ResourceItemEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(resourceMapper.toItemDto(any(ResourceItemEntity.class))).thenReturn(resourceItemDTO);

        ResponseEntity<ResourceItemDTO> response = handler.handle(command);

        assertNotNull(response);
        assertEquals(resourceItemDTO, response.getBody());
        assertEquals(2, resourceItemEntity.getPermissions().size());
        verify(resourceItemEntityRepository).findByName("ItemA");
        verify(permissionEntityRepository).findAllByNameIn(anyList());
        verify(resourceItemEntityRepository).save(any(ResourceItemEntity.class));
        verify(resourceMapper).toItemDto(any(ResourceItemEntity.class));
    }

    @Test
    void testHandle_ShouldThrow_WhenResourceItemNotFound() {
        when(resourceItemEntityRepository.findByName("ItemA")).thenReturn(Optional.empty());

        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class, () -> handler.handle(command));

        assertTrue(ex.getMessage().contains("Resource item not found"));
        verify(resourceItemEntityRepository).findByName("ItemA");
        verifyNoInteractions(permissionEntityRepository);
    }

    @Test
    void testHandle_ShouldThrow_WhenNoValidPermissionsFound() {
        when(resourceItemEntityRepository.findByName("ItemA")).thenReturn(Optional.of(resourceItemEntity));
        when(permissionEntityRepository.findAllByNameIn(List.of("READ", "WRITE")))
                .thenReturn(List.of());

        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class, () -> handler.handle(command));

        assertTrue(ex.getMessage().contains("No valid permissions found"));
        verify(resourceItemEntityRepository).findByName("ItemA");
        verify(permissionEntityRepository).findAllByNameIn(anyList());
        verify(resourceItemEntityRepository, never()).save(any());
    }
}