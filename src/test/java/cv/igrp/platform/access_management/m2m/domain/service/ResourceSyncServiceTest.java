package cv.igrp.platform.access_management.m2m.domain.service;

import cv.igrp.platform.access_management.department.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.ResourceDTO;
import cv.igrp.platform.access_management.shared.application.dto.ResourceItemDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceItemEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ResourceEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ResourceItemEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResourceSyncServiceTest {

    @Mock private ResourceEntityRepository resourceRepository;
    @Mock private ResourceItemEntityRepository resourceItemEntityRepository;
    @Mock private PermissionEntityRepository permissionRepository;
    @Mock private ResourceMapper resourceMapper;

    @InjectMocks
    private ResourceSyncService service;

    private ResourceDTO dto;

    @BeforeEach
    void setUp() {
        dto = new ResourceDTO();
        dto.setName("res-1");
        dto.setDescription("desc");
        dto.setStatus(Status.ACTIVE);
        dto.setItems(new ArrayList<>());
    }

    @Test
    void synchronize_NewResource_Created() {
        when(resourceRepository.findByNameAndStatusNot("res-1", Status.DELETED)).thenReturn(Optional.empty());
        ResourceEntity newEntity = new ResourceEntity();
        when(resourceMapper.toEntity(dto)).thenReturn(newEntity);

        service.synchronizeResource(dto);

        verify(resourceRepository).save(newEntity);
    }

    @Test
    void synchronize_ExistingMatches_NoUpdate() {
        ResourceEntity existing = new ResourceEntity();
        existing.setName("res-1");
        existing.setDescription("desc");
        existing.setStatus(Status.ACTIVE);
        when(resourceRepository.findByNameAndStatusNot("res-1", Status.DELETED)).thenReturn(Optional.of(existing));

        ResourceDTO mappedExisting = new ResourceDTO();
        mappedExisting.setName(dto.getName());
        mappedExisting.setDescription(dto.getDescription());
        mappedExisting.setStatus(dto.getStatus());
        mappedExisting.setItems(new ArrayList<>());
        when(resourceMapper.toDto(existing)).thenReturn(mappedExisting);

        service.synchronizeResource(dto);

        verify(resourceRepository, never()).save(any());
    }

    @Test
    void synchronize_ExistingDiffers_Updated() {
        ResourceEntity existing = new ResourceEntity();
        existing.setName("res-1");
        existing.setDescription("old");
        existing.setStatus(Status.ACTIVE);
        existing.setItems(new ArrayList<>());
        when(resourceRepository.findByNameAndStatusNot("res-1", Status.DELETED)).thenReturn(Optional.of(existing));

        ResourceDTO oldMapped = new ResourceDTO();
        oldMapped.setName("res-1");
        oldMapped.setDescription("old");
        oldMapped.setStatus(Status.ACTIVE);
        oldMapped.setItems(new ArrayList<>());
        when(resourceMapper.toDto(existing)).thenReturn(oldMapped);

        service.synchronizeResource(dto);

        assertEquals("desc", existing.getDescription());
        verify(resourceRepository).save(existing);
    }

    @Test
    void synchronize_ExistingWithItemDeletion_RemovesOrphans() {
        ResourceEntity existing = new ResourceEntity();
        existing.setName("res-1");
        existing.setDescription("old");
        existing.setStatus(Status.ACTIVE);
        ResourceItemEntity orphan = new ResourceItemEntity();
        orphan.setId(99);
        orphan.setName("gone");
        orphan.setPermissions(new ArrayList<>());
        existing.setItems(new ArrayList<>(List.of(orphan)));
        when(resourceRepository.findByNameAndStatusNot("res-1", Status.DELETED)).thenReturn(Optional.of(existing));

        ResourceDTO oldMapped = new ResourceDTO();
        oldMapped.setName("res-1");
        oldMapped.setDescription("old-different");
        oldMapped.setStatus(Status.ACTIVE);
        when(resourceMapper.toDto(existing)).thenReturn(oldMapped);

        // Incoming has no items
        dto.setItems(new ArrayList<>());

        service.synchronizeResource(dto);

        verify(resourceItemEntityRepository).deleteById(99);
        verify(resourceRepository).save(existing);
    }

    @Test
    void synchronize_ExistingWithMatchingItem_UpdatesPermissions() {
        ResourceEntity existing = new ResourceEntity();
        existing.setName("res-1");
        existing.setDescription("old");
        existing.setStatus(Status.ACTIVE);
        ResourceItemEntity item = new ResourceItemEntity();
        item.setId(1);
        item.setName("item-a");
        item.setPermissions(new ArrayList<>());
        existing.setItems(new ArrayList<>(List.of(item)));
        when(resourceRepository.findByNameAndStatusNot("res-1", Status.DELETED)).thenReturn(Optional.of(existing));

        ResourceDTO oldMapped = new ResourceDTO();
        oldMapped.setName("res-1");
        oldMapped.setDescription("differs");
        oldMapped.setStatus(Status.ACTIVE);
        when(resourceMapper.toDto(existing)).thenReturn(oldMapped);

        ResourceItemDTO itemDto = new ResourceItemDTO();
        itemDto.setName("item-a");
        itemDto.setPermissions(List.of("PERM_A"));
        dto.setItems(new ArrayList<>(List.of(itemDto)));

        PermissionEntity perm = new PermissionEntity();
        perm.setName("PERM_A");
        when(permissionRepository.findAllByNameIn(List.of("PERM_A"))).thenReturn(List.of(perm));

        service.synchronizeResource(dto);

        verify(resourceRepository).save(existing);
    }

    @Test
    void synchronize_ExistingWithNewItem_Added() {
        ResourceEntity existing = new ResourceEntity();
        existing.setName("res-1");
        existing.setDescription("old");
        existing.setStatus(Status.ACTIVE);
        existing.setItems(new ArrayList<>());
        when(resourceRepository.findByNameAndStatusNot("res-1", Status.DELETED)).thenReturn(Optional.of(existing));

        ResourceDTO oldMapped = new ResourceDTO();
        oldMapped.setName("res-1");
        oldMapped.setDescription("differs");
        oldMapped.setStatus(Status.ACTIVE);
        when(resourceMapper.toDto(existing)).thenReturn(oldMapped);

        ResourceItemDTO itemDto = new ResourceItemDTO();
        itemDto.setName("new-item");
        dto.setItems(new ArrayList<>(List.of(itemDto)));

        ResourceItemEntity newItem = new ResourceItemEntity();
        newItem.setName("new-item");
        when(resourceMapper.toItemEntity(itemDto, existing)).thenReturn(newItem);

        service.synchronizeResource(dto);

        assertTrue(existing.getItems().contains(newItem));
        verify(resourceRepository).save(existing);
    }
}
