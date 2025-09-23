package cv.igrp.platform.access_management.menu.mapper;

import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import org.springframework.stereotype.Component;

@Component
public class MenuEntryMapper {

    public MenuEntryDTO toDTO(MenuEntryEntity entity) {
        if (entity == null) return null;

        MenuEntryDTO dto = new MenuEntryDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setCode(entity.getCode());
        dto.setType(entity.getType());
        dto.setPosition(entity.getPosition());
        dto.setIcon(entity.getIcon());
        dto.setStatus(entity.getStatus());
        dto.setTarget(entity.getTarget());
        dto.setUrl(entity.getUrl());
        dto.setPageSlug(entity.getPageSlug());
        dto.setPermissions(entity.getPermissions().stream()
                .map(PermissionEntity::getName)
                .toList());
        if (entity.getParentId() != null)
            dto.setParentCode(entity.getParentId().getCode());

        if (entity.getApplicationId() != null)
            dto.setApplicationCode(entity.getApplicationId().getCode());

        dto.setCreatedBy(entity.getCreatedBy());
        if(entity.getCreatedDate() != null)
            dto.setCreatedDate(entity.getCreatedDate().toString());
        dto.setLastModifiedBy(entity.getLastModifiedBy());
        if(entity.getLastModifiedDate() != null)
            dto.setLastModifiedDate(entity.getLastModifiedDate().toString());

        return dto;
    }

    public MenuEntryEntity toEntity(MenuEntryDTO dto) {
        if (dto == null) return null;

        MenuEntryEntity entity = new MenuEntryEntity();
        entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setCode(dto.getCode());
        entity.setType(dto.getType());
        entity.setPosition(dto.getPosition());
        entity.setIcon(dto.getIcon());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : Status.ACTIVE);
        entity.setTarget(dto.getTarget());
        entity.setUrl(dto.getUrl());
        entity.setPageSlug(dto.getPageSlug());

        // parentId, applicationId, and resourceId should be set in the service layer using repositories
        // like MenuEntryEntity.setParentId(menuRepo.findById(dto.getParentId()).orElse(null))

        return entity;
    }
}

