package cv.igrp.platform.access_management.resource.mapper;

import cv.igrp.platform.access_management.shared.application.constants.ResourceType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.ResourceDTO;
import cv.igrp.platform.access_management.shared.application.dto.ResourceItemDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceItemEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ResourceItemEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceMapperTest {

    @Mock
    private PermissionEntityRepository permissionRepository;

    @Mock
    ResourceItemEntityRepository resourceItemRepository;

    private ResourceMapper mapper;

    private ResourceEntity testResource;
    private ResourceDTO testResourceDTO;
    private ResourceItemEntity testResourceItem;
    private ResourceItemDTO testResourceItemDTO;
    private PermissionEntity testPermission;

    @BeforeEach
    void setUp() {
        mapper = new ResourceMapper(permissionRepository, resourceItemRepository);

        var testApplication = new ApplicationEntity();
        testApplication.setId(123);
        testApplication.setCode("APP");

        testPermission = new PermissionEntity();
        testPermission.setId(456);
        testPermission.setName("permission456");

        testResourceItem = new ResourceItemEntity();
        testResourceItem.setId(789);
        testResourceItem.setName("test");
        testResourceItem.setDescription("Test Item");
        testResourceItem.setUrl("/api/test");
        testResourceItem.setPermissionId(testPermission.getId());

        testResource = new ResourceEntity();
        testResource.setId(123);
        testResource.setName("test");
        testResource.setDescription("Test Resource");
        testResource.setType(ResourceType.API);
        testResource.setStatus(Status.ACTIVE);
        testResource.setExternalId("ext-123");
        testResource.setApplications(Set.of(testApplication));
        testResource.setItems(new ArrayList<>(List.of(testResourceItem)));

        testResourceItem.setResourceId(testResource);

        testResourceItemDTO = new ResourceItemDTO();
        testResourceItemDTO.setId(789);
        testResourceItemDTO.setName("test");
        testResourceItemDTO.setDescription("Test Item");
        testResourceItemDTO.setUrl("/api/test");
        testResourceItemDTO.setResourceName("test");
        testResourceItemDTO.setPermissionName("permission456");

        testResourceDTO = new ResourceDTO();
        testResourceDTO.setName("test");
        testResourceDTO.setDescription("Test Resource");
        testResourceDTO.setType(ResourceType.API);
        testResourceDTO.setStatus(Status.ACTIVE);
        testResourceDTO.setExternalId("ext-123");
        testResourceDTO.setApplications(List.of("APP"));
        testResourceDTO.setItems(List.of(testResourceItemDTO));

    }

    @Test
    void toDto_shouldMapAllFieldsCorrectly() {
        when(permissionRepository.findById(456)).thenReturn(Optional.of(testPermission));

        ResourceDTO result = mapper.toDto(testResource);

        assertNotNull(result);
        assertEquals(testResource.getId(), result.getId());
        assertEquals(testResource.getName(), result.getName());
        assertEquals(testResource.getType(), result.getType());
        assertEquals(testResource.getStatus(), result.getStatus());
        assertEquals(testResource.getExternalId(), result.getExternalId());
        assertTrue(result.getApplications().contains("APP"));
        assertNotNull(result.getItems());
        assertEquals(1, result.getItems().size());

        ResourceItemDTO itemDto = result.getItems().getFirst();
        assertEquals("permission456", itemDto.getPermissionName());
    }

    @Test
    void toDto_shouldHandleNullValues() {
        ResourceEntity resource = new ResourceEntity();
        resource.setId(1);

        ResourceDTO result = mapper.toDto(resource);

        assertNotNull(result);
        assertEquals(1, result.getId());
        assertNull(result.getName());
        assertNull(result.getType());
        assertNull(result.getStatus());
        assertNull(result.getExternalId());
        assertTrue(result.getApplications().isEmpty());
        assertEquals(result.getItems(), List.of());
    }

    @Test
    void toDto_shouldReturnNullForNullInput() {
        assertNull(mapper.toDto(null));
    }

    @Test
    void toItemDto_shouldMapAllFieldsCorrectly() {

        when(permissionRepository.findById(456)).thenReturn(Optional.of(testPermission));

        ResourceItemDTO result = mapper.toItemDto(testResourceItem);

        assertNotNull(result);
        assertEquals(testResourceItem.getId(), result.getId());
        assertEquals(testResourceItem.getName(), result.getName());
        assertEquals(testResourceItem.getUrl(), result.getUrl());
        assertEquals(testResourceItem.getResourceId().getName(), result.getResourceName());
        assertEquals("permission456", result.getPermissionName());
    }

    @Test
    void toItemDto_shouldHandleNullValues() {
        ResourceItemEntity item = new ResourceItemEntity();
        item.setId(1);

        ResourceItemDTO result = mapper.toItemDto(item);

        assertNotNull(result);
        assertEquals(1, result.getId());
        assertNull(result.getName());
        assertNull(result.getUrl());
        assertNull(result.getResourceName());
        assertNull(result.getPermissionName());
    }

    @Test
    void toItemDto_shouldReturnNullForNullInput() {
        assertNull(mapper.toItemDto(null));
    }

    @Test
    void toEntity_shouldMapBasicFieldsCorrectly() {
        ResourceEntity result = mapper.toEntity(testResourceDTO);

        assertNotNull(result);
        assertEquals(testResourceDTO.getName(), result.getName());
        assertEquals(testResourceDTO.getType(), result.getType());
        assertEquals(testResourceDTO.getStatus(), result.getStatus());
        assertEquals(testResourceDTO.getExternalId(), result.getExternalId());
        assertNull(result.getId());
        assertTrue(result.getApplications().isEmpty());
    }

    @Test
    void toEntity_shouldSetStatusToActive_When_NotProvided() {
        testResourceDTO.setStatus(null);

        ResourceEntity result = mapper.toEntity(testResourceDTO);

        assertEquals(Status.ACTIVE, result.getStatus());
    }

    @Test
    void toEntity_shouldReturnNullForNullInput() {
        assertNull(mapper.toEntity(null));
    }

    @Test
    void toItemEntity_shouldMapBasicFieldsCorrectly() {

        when(resourceItemRepository.save(Mockito.any(ResourceItemEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ResourceItemEntity result = mapper.toItemEntity(testResourceItemDTO, testResource, testPermission);

        assertNotNull(result);
        assertEquals(testResourceItemDTO.getName(), result.getName());
        assertEquals(testResourceItemDTO.getUrl(), result.getUrl());
        assertSame(testResource, result.getResourceId());
        assertSame(testPermission.getId(), result.getPermissionId());
    }

    @Test
    void toItemEntity_shouldReturnNullForNullInput() {
        assertNull(mapper.toItemEntity(null, testResource, testPermission));
    }

    @Test
    void toDto_shouldHandleMultipleItems() {
        ResourceItemEntity item1 = new ResourceItemEntity();
        item1.setId(1);
        item1.setName("Item1");
        item1.setResourceId(testResource);
        item1.setPermissionId(testPermission.getId());

        ResourceItemEntity item2 = new ResourceItemEntity();
        item2.setId(2);
        item2.setName("Item2");
        item2.setResourceId(testResource);
        item2.setPermissionId(testPermission.getId());

        testResource.setItems(Arrays.asList(item1, item2));

        when(permissionRepository.findById(testPermission.getId())).thenReturn(Optional.of(testPermission));

        ResourceDTO result = mapper.toDto(testResource);

        assertNotNull(result);
        assertNotNull(result.getItems());
        assertEquals(2, result.getItems().size());
        assertEquals(1, result.getItems().get(0).getId());
        assertEquals(2, result.getItems().get(1).getId());
    }

    @Test
    void toDto_shouldHandleNullItems() {
        testResource.setItems(null);

        ResourceDTO result = mapper.toDto(testResource);

        assertNotNull(result);
        assertEquals(result.getItems(), List.of());
    }
}
