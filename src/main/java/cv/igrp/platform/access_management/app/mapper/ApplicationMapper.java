package cv.igrp.platform.access_management.app.mapper;

import cv.igrp.platform.access_management.app.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * Mapper component responsible for converting between {@link Application} entities
 * and {@link ApplicationDTO} data transfer objects.
 * <p>
 * This class centralizes the mapping logic to isolate transformation concerns,
 * ensuring that conversion between the internal domain model and external
 * representations is consistent and reusable.
 * </p>
 *
 * <p>Conversion rules:</p>
 * <ul>
 *     <li>Null-safe transformations.</li>
 *     <li>Handles {@link java.net.URI} conversion for the {@code url} field.</li>
 *     <li>Date fields are converted to ISO-8601 string format.</li>
 * </ul>
 *
 * @see Application
 * @see ApplicationDTO
 */
@Component
public class ApplicationMapper {

    /**
     * Converts an {@link Application} entity to an {@link ApplicationDTO}.
     *
     * @param entity the {@link Application} entity to be converted; may be {@code null}
     * @return a fully populated {@link ApplicationDTO} or {@code null} if input is {@code null}
     */
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

    /**
     * Converts an {@link ApplicationDTO} to an {@link Application} entity.
     *
     * @param dto the {@link ApplicationDTO} to be converted; may be {@code null}
     * @return a fully populated {@link Application} entity or {@code null} if input is {@code null}
     */
    public Application toEntity(ApplicationDTO dto) {
        if (dto == null) return null;

        Application entity = new Application();
        entity.setId(dto.getId());
        entity.setCode(dto.getCode());
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : Status.ACTIVE);
        entity.setType(dto.getType());
        entity.setOwner(dto.getOwner());
        entity.setPicture(dto.getPicture());
        entity.setUrl(dto.getUrl() != null ? dto.getUrl().toString() : null);
        entity.setSlug(dto.getSlug());

        return entity;
    }
}

