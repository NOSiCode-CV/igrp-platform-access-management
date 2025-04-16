package cv.igrp.platform.access_management.menu_entry.mapper;

import cv.igrp.platform.access_management.menu_entry.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.shared.domain.models.MenuEntry;
import org.springframework.stereotype.Component;

@Component
public class MenuEntryMapper {

    public MenuEntryDTO toDTO(MenuEntry entity) {
        if (entity == null) return null;

        MenuEntryDTO dto = new MenuEntryDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setType(entity.getType());
        dto.setPosition(entity.getPosition());
        dto.setIcon(entity.getIcon());
        dto.setStatus(entity.getStatus());
        dto.setTarget(entity.getTarget());
        dto.setUrl(entity.getUrl());

        if (entity.getParentId() != null)
            dto.setParentId(entity.getParentId().getId());

        if (entity.getApplicationId() != null)
            dto.setApplicationId(entity.getApplicationId().getId());

        if (entity.getResourceId() != null)
            dto.setResourceId(entity.getResourceId().getId());

        dto.setCreatedBy(entity.getCreatedBy());
        if(entity.getCreatedDate() != null)
            dto.setCreatedDate(entity.getCreatedDate().toString());
        dto.setLastModifiedBy(entity.getLastModifiedBy());
        if(entity.getLastModifiedDate() != null)
            dto.setLastModifiedDate(entity.getLastModifiedDate().toString());

        return dto;
    }

    public MenuEntry toEntity(MenuEntryDTO dto) {
        if (dto == null) return null;

        MenuEntry entity = new MenuEntry();
        entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setType(dto.getType());
        entity.setPosition(dto.getPosition());
        entity.setIcon(dto.getIcon());
        entity.setStatus(dto.getStatus());
        entity.setTarget(dto.getTarget());
        entity.setUrl(dto.getUrl());

        // parentId, applicationId, and resourceId should be set in the service layer using repositories
        // like menuEntry.setParentId(menuRepo.findById(dto.getParentId()).orElse(null))

        return entity;
    }
}

