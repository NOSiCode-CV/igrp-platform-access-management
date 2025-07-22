package cv.igrp.platform.access_management.resource.mapper;

import cv.igrp.platform.access_management.resource.application.dto.ResourceDTO;
import cv.igrp.platform.access_management.resource.application.dto.ResourceItemDTO;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceItemEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ResourceMapper {

    private final PermissionEntityRepository permissionRepository;

    public ResourceMapper(PermissionEntityRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    public ResourceDTO toDto(ResourceEntity resource) {
        if (resource == null) return null;
        ResourceDTO dto = new ResourceDTO();
        dto.setId(resource.getId());
        dto.setName(resource.getName());
        dto.setDescription(resource.getDescription());
        dto.setType(resource.getType());
        dto.setStatus(resource.getStatus());
        dto.setExternalId(resource.getExternalId());
        dto.setApplicationCode(resource.getApplicationId() != null ? resource.getApplicationId().getCode() : null);
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

    public ResourceItemDTO toItemDto(ResourceItemEntity item) {
        if (item == null) return null;
        ResourceItemDTO dto = new ResourceItemDTO();
        dto.setId(item.getId());
        dto.setName(item.getName());
        dto.setDescription(item.getDescription());
        dto.setUrl(item.getUrl());
        dto.setResourceName(item.getResourceId() != null ? item.getResourceId().getName() : null);
        dto.setPermissionName(item.getPermissionId() != null ? permissionRepository.findById(
                item.getPermissionId()).map(PermissionEntity::getName).orElse(null): null);
        dto.setCreatedBy(item.getCreatedBy());
        if(item.getCreatedDate() != null)
            dto.setCreatedDate(item.getCreatedDate().toString());
        dto.setLastModifiedBy(item.getLastModifiedBy());
        if(item.getLastModifiedDate() != null)
            dto.setLastModifiedDate(item.getLastModifiedDate().toString());
        return dto;
    }

    public ResourceEntity toEntity(ResourceDTO dto) {
        if (dto == null) return null;

        ResourceEntity resource = new ResourceEntity();
        resource.setName(dto.getName());
        resource.setDescription(dto.getDescription());
        resource.setType(dto.getType());
        resource.setStatus(dto.getStatus() != null ? dto.getStatus() : Status.ACTIVE);
        resource.setExternalId(dto.getExternalId());

        return resource;
    }

    public ResourceItemEntity toItemEntity(ResourceItemDTO dto, ResourceEntity parentResource, PermissionEntity permission) {
        if (dto == null) return null;
        ResourceItemEntity item = new ResourceItemEntity();
        item.setName(dto.getName());
        item.setDescription(dto.getDescription());
        item.setUrl(dto.getUrl());
        item.setResourceId(parentResource);
        item.setPermissionId(permission.getId());
        return item;
    }
}

