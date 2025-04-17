package cv.igrp.platform.access_management.app.mapper;

import cv.igrp.platform.access_management.app.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class ApplicationMapper {

    public ApplicationDTO toDto(Application entity) {
        if (entity == null) return null;
        ApplicationDTO dto = new ApplicationDTO();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setStatus(entity.getStatus());
        dto.setType(entity.getType());
        dto.setOwner(entity.getOwner());
        dto.setPicture(entity.getPicture());
        dto.setUrl(entity.getUrl() != null ? URI.create(entity.getUrl()) : null);
        dto.setSlug(entity.getSlug());
        dto.setCreatedBy(entity.getCreatedBy());
        if(entity.getCreatedDate() != null)
            dto.setCreatedDate(entity.getCreatedDate().toString());
        dto.setLastModifiedBy(entity.getLastModifiedBy());
        if(entity.getLastModifiedDate() != null)
            dto.setLastModifiedDate(entity.getLastModifiedDate().toString());
        return dto;
    }

    public Application toEntity(ApplicationDTO dto) {
        if (dto == null) return null;

        Application entity = new Application();
        entity.setId(dto.getId());
        entity.setCode(dto.getCode());
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setStatus(dto.getStatus());
        entity.setType(dto.getType());
        entity.setOwner(dto.getOwner());
        entity.setPicture(dto.getPicture());
        entity.setUrl(dto.getUrl() != null ? dto.getUrl().toString() : null);
        entity.setSlug(dto.getSlug());

        return entity;
    }
}

