package cv.igrp.platform.access_management.resource.mapper;

import cv.igrp.platform.access_management.resource.application.dto.ResourceDTO;
import cv.igrp.platform.access_management.resource.application.dto.ResourceItemDTO;
import cv.igrp.platform.access_management.shared.domain.models.Permission;
import cv.igrp.platform.access_management.shared.domain.models.Resource;
import cv.igrp.platform.access_management.shared.domain.models.ResourceItem;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ResourceMapper {

    public ResourceDTO toDto(Resource resource) {
        if (resource == null) return null;
        ResourceDTO dto = new ResourceDTO();
        dto.setId(resource.getId());
        dto.setName(resource.getName());
        dto.setType(resource.getType());
        dto.setStatus(resource.getStatus());
        dto.setExternalId(resource.getExternalId());
        dto.setApplicationId(resource.getApplicationId() != null ? resource.getApplicationId().getId() : null);
        if (resource.getItems() != null) {
            List<ResourceItemDTO> items = resource.getItems().stream().map(this::toItemDto).toList();
            dto.setItems(items);
        }
        dto.setCreatedBy(resource.getCreatedBy());
        if(resource.getCreatedDate() != null)
            dto.setCreatedDate(resource.getCreatedDate().toString());
        dto.setLastModifiedBy(resource.getLastModifiedBy());
        if(resource.getLastModifiedDate() != null)
            dto.setLastModifiedDate(resource.getLastModifiedDate().toString());
        return dto;
    }

    public ResourceItemDTO toItemDto(ResourceItem item) {
        if (item == null) return null;
        ResourceItemDTO dto = new ResourceItemDTO();
        dto.setId(item.getId());
        dto.setName(item.getName());
        dto.setUrl(item.getUrl());
        dto.setResourceId(item.getResourceId() != null ? item.getResourceId().getId() : null);
        dto.setPermissionId(item.getPermissionId() != null ? item.getPermissionId().getId() : null);
        dto.setCreatedBy(item.getCreatedBy());
        if(item.getCreatedDate() != null)
            dto.setCreatedDate(item.getCreatedDate().toString());
        dto.setLastModifiedBy(item.getLastModifiedBy());
        if(item.getLastModifiedDate() != null)
            dto.setLastModifiedDate(item.getLastModifiedDate().toString());
        return dto;
    }

    public Resource toEntity(ResourceDTO dto) {
        if (dto == null) return null;

        Resource resource = new Resource();
        resource.setName(dto.getName());
        resource.setType(dto.getType());
        resource.setStatus(dto.getStatus());
        resource.setExternalId(dto.getExternalId());

        return resource;
    }

    public ResourceItem toItemEntity(ResourceItemDTO dto, Resource parentResource, Permission permission) {
        if (dto == null) return null;
        ResourceItem item = new ResourceItem();
        item.setName(dto.getName());
        item.setUrl(dto.getUrl());
        item.setResourceId(parentResource);
        item.setPermissionId(permission);
        return item;
    }
}

