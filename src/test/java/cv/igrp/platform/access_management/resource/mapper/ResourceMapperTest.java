package cv.igrp.platform.access_management.resource.mapper;

import cv.igrp.platform.access_management.resource.application.dto.ResourceDTO;
import cv.igrp.platform.access_management.resource.application.dto.ResourceItemDTO;
import cv.igrp.platform.access_management.shared.application.constants.ResourceType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.domain.models.Permission;
import cv.igrp.platform.access_management.shared.domain.models.Resource;
import cv.igrp.platform.access_management.shared.domain.models.ResourceItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class ResourceMapperTest {

    private ResourceMapper mapper;
    private Resource testResource;
    private ResourceDTO testResourceDTO;
    private ResourceItem testResourceItem;
    private ResourceItemDTO testResourceItemDTO;
    private Permission testPermission;
    private Application testApplication;

    @BeforeEach
    void setUp() {
        // Arrange
        mapper = new ResourceMapper();


        testApplication = new Application();
        testApplication.setId(123);


        testPermission = new Permission();
        testPermission.setId(123);

        testResource = new Resource();
        testResource.setId(123);
        testResource.setName("Test Resource");
        testResource.setType(ResourceType.API);
        testResource.setStatus(Status.ACTIVE);
        testResource.setExternalId("ext-123");
        testResource.setApplicationId(testApplication);


        testResourceItem = new ResourceItem();
        testResourceItem.setId(123);
        testResourceItem.setName("Test Item");
        testResourceItem.setUrl("/api/test");
        testResourceItem.setResourceId(testResource);
        testResourceItem.setPermissionId(testPermission);

        // Set up test resource with items
        testResource.setItems(Collections.singletonList(testResourceItem));

        // Set up test resource DTO
        testResourceDTO = new ResourceDTO();
        testResourceDTO.setName("Test Resource DTO");
        testResourceDTO.setType(ResourceType.UI);
        testResourceDTO.setStatus(Status.INACTIVE);
        testResourceDTO.setExternalId("ext-456");
        testResourceDTO.setApplicationId(456);

        // Set up test resource item DTO
        testResourceItemDTO = new ResourceItemDTO();
        testResourceItemDTO.setName("Test Item DTO");
        testResourceItemDTO.setUrl("/api/test-dto");
        testResourceItemDTO.setResourceId(456);
        testResourceItemDTO.setPermissionId(456);
    }

    @Test
    void toDto_shouldMapAllFieldsCorrectly() {
        // Arrange - already done in setUp()

        // Act
        ResourceDTO result = mapper.toDto(testResource);

        // Assert
        assertNotNull(result);
        assertEquals(testResource.getId(), result.getId());
        assertEquals(testResource.getName(), result.getName());
        assertEquals(testResource.getType(), result.getType());
        assertEquals(testResource.getStatus(), result.getStatus());
        assertEquals(testResource.getExternalId(), result.getExternalId());
        assertEquals(testResource.getApplicationId().getId(), result.getApplicationId());
        assertEquals(testResource.getCreatedBy(), result.getCreatedBy());
        assertNotNull(result.getItems());
        assertEquals(1, result.getItems().size());
    }

    @Test
    void toDto_shouldHandleNullValues() {
        // Arrange
        Resource resource = new Resource();
        resource.setId(1);
        // All other fields are null

        // Act
        ResourceDTO result = mapper.toDto(resource);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getId());
        assertNull(result.getName());
        assertNull(result.getType());
        assertNull(result.getStatus());
        assertNull(result.getExternalId());
        assertNull(result.getApplicationId());
        assertNull(result.getItems());
        assertNull(result.getCreatedBy());
        assertNull(result.getCreatedDate());
        assertNull(result.getLastModifiedBy());
        assertNull(result.getLastModifiedDate());
    }

    @Test
    void toDto_shouldReturnNullForNullInput() {
        // Arrange - null input

        // Act
        ResourceDTO result = mapper.toDto(null);

        // Assert
        assertNull(result);
    }

    @Test
    void toItemDto_shouldMapAllFieldsCorrectly() {
        // Arrange - already done in setUp()

        // Act
        ResourceItemDTO result = mapper.toItemDto(testResourceItem);

        // Assert
        assertNotNull(result);
        assertEquals(testResourceItem.getId(), result.getId());
        assertEquals(testResourceItem.getName(), result.getName());
        assertEquals(testResourceItem.getUrl(), result.getUrl());
        assertEquals(testResourceItem.getResourceId().getId(), result.getResourceId());
        assertEquals(testResourceItem.getPermissionId().getId(), result.getPermissionId());
    }

    @Test
    void toItemDto_shouldHandleNullValues() {
        // Arrange
        ResourceItem item = new ResourceItem();
        item.setId(1);
        // All other fields are null

        // Act
        ResourceItemDTO result = mapper.toItemDto(item);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getId());
        assertNull(result.getName());
        assertNull(result.getUrl());
        assertNull(result.getResourceId());
        assertNull(result.getPermissionId());
    }

    @Test
    void toItemDto_shouldReturnNullForNullInput() {
        // Arrange

        // Act
        ResourceItemDTO result = mapper.toItemDto(null);

        // Assert
        assertNull(result);
    }

    @Test
    void toEntity_shouldMapBasicFieldsCorrectly() {
        // Arrange - already done in setUp()

        // Act
        Resource result = mapper.toEntity(testResourceDTO);

        // Assert
        assertNotNull(result);
        assertEquals(testResourceDTO.getName(), result.getName());
        assertEquals(testResourceDTO.getType(), result.getType());
        assertEquals(testResourceDTO.getStatus(), result.getStatus());
        assertEquals(testResourceDTO.getExternalId(), result.getExternalId());

        // Assert - fields that should not be mapped
        assertNull(result.getId(), "ID should not be mapped");
        assertNull(result.getApplicationId(), "ApplicationId should not be mapped");
    }

    @Test
    void toEntity_shouldSetStatusToActive_When_NotProvided() {
        // Arrange - already done in setUp()
        testResourceDTO.setStatus(null);

        // Act
        Resource result = mapper.toEntity(testResourceDTO);

        // Assert
        assertNotNull(result);
        assertEquals(testResourceDTO.getName(), result.getName());
        assertEquals(testResourceDTO.getType(), result.getType());
        assertEquals(Status.ACTIVE, result.getStatus());
        assertEquals(testResourceDTO.getExternalId(), result.getExternalId());

        // Assert - fields that should not be mapped
        assertNull(result.getId(), "ID should not be mapped");
        assertNull(result.getApplicationId(), "ApplicationId should not be mapped");
    }

    @Test
    void toEntity_shouldReturnNullForNullInput() {
        // Arrange - null input

        // Act
        Resource result = mapper.toEntity(null);

        // Assert
        assertNull(result);
    }

    @Test
    void toItemEntity_shouldMapBasicFieldsCorrectly() {
        // Arrange - already done in setUp()

        // Act
        ResourceItem result = mapper.toItemEntity(testResourceItemDTO, testResource, testPermission);

        // Assert
        assertNotNull(result);
        assertEquals(testResourceItemDTO.getName(), result.getName());
        assertEquals(testResourceItemDTO.getUrl(), result.getUrl());
        assertSame(testResource, result.getResourceId(), "Resource reference should be the same object");
        assertSame(testPermission, result.getPermissionId(), "Permission reference should be the same object");

        // Assert - fields that should not be mapped
        assertNull(result.getId(), "ID should not be mapped");
    }

    @Test
    void toItemEntity_shouldReturnNullForNullInput() {
        // Arrange - null input

        // Act
        ResourceItem result = mapper.toItemEntity(null, testResource, testPermission);

        // Assert
        assertNull(result);
    }

    @Test
    void toDto_shouldHandleMultipleItems() {
        // Arrange
        ResourceItem item1 = new ResourceItem();
        item1.setId(1);
        item1.setName("Item 1");
        item1.setResourceId(testResource);
        item1.setPermissionId(testPermission);

        ResourceItem item2 = new ResourceItem();
        item2.setId(2);
        item2.setName("Item 2");
        item2.setResourceId(testResource);
        item2.setPermissionId(testPermission);

        testResource.setItems(Arrays.asList(item1, item2));

        // Act
        ResourceDTO result = mapper.toDto(testResource);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getItems(), "Items list should not be null");
        assertEquals(2, result.getItems().size(), "Should have 2 items");
        assertEquals(1, result.getItems().get(0).getId(), "First item ID should match");
        assertEquals(2, result.getItems().get(1).getId(), "Second item ID should match");
    }

    @Test
    void toDto_shouldHandleNullItems() {
        // Arrange
        testResource.setItems(null);

        // Act
        ResourceDTO result = mapper.toDto(testResource);

        // Assert
        assertNotNull(result);
        assertNull(result.getItems(), "Items should be null when source items are null");
    }
}
