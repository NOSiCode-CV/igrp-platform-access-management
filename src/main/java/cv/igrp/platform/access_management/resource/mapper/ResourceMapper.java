package cv.igrp.platform.access_management.resource.mapper;

import cv.igrp.platform.access_management.resource.application.dto.ResourceDTO;
import cv.igrp.platform.access_management.resource.application.dto.ResourceItemDTO;
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
        dto.setApplicationId(resource.getApplicationId() != null ? resource.getApplicationId().getId() : null);
        if (resource.getItems() != null) {
            List<ResourceItemDTO> items = resource.getItems().stream().map(this::toItemDto).toList();
            dto.setItems(items);
        }
        return dto;
    }

    public ResourceItemDTO toItemDto(ResourceItem item) {
        if (item == null) return null;
        ResourceItemDTO dto = new ResourceItemDTO();
        dto.setId(item.getId());
        dto.setName(item.getName());
        dto.setUrl(item.getUrl());
        return dto;
    }

    public Resource toEntity(ResourceDTO dto) {
        if (dto == null) return null;

        Resource resource = new Resource();
        resource.setName(dto.getName());
        resource.setType(dto.getType());
        resource.setStatus(dto.getStatus());

        return resource;
    }

    public ResourceItem toItemEntity(ResourceItemDTO dto, Resource parentResource) {
        if (dto == null) return null;
        ResourceItem item = new ResourceItem();
        item.setName(dto.getName());
        item.setUrl(dto.getUrl());
        item.setResourceId(parentResource);
        return item;
    }
}

