package cv.igrp.platform.access_management.global_configuration.mapper;

import cv.igrp.platform.access_management.global_configuration.application.dto.GlobalConfigurationDTO;
import cv.igrp.platform.access_management.global_configuration.domain.models.GlobalConfiguration;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * Mapper component responsible for converting between {@link GlobalConfiguration} entities
 * and {@link GlobalConfigurationDTO} data transfer objects.
 * <p>
 * This class centralizes the mapping logic to isolate transformation concerns,
 * ensuring that conversion between the internal domain model and external
 * representations is consistent and reusable.
 * </p>
 *
 * <p>Conversion rules:</p>
 * <ul>
 *     <li>Null-safe transformations.</li>
 *     <li>Handles {@link URI} conversion for the {@code url} field.</li>
 *     <li>Date fields are converted to ISO-8601 string format.</li>
 * </ul>
 *
 * @see GlobalConfiguration
 * @see GlobalConfigurationDTO
 */
@Component
public class GlobalConfigurationMapper {

    /**
     * Converts an {@link GlobalConfiguration} entity to an {@link GlobalConfigurationDTO}.
     *
     * @param entity the {@link GlobalConfiguration} entity to be converted; may be {@code null}
     * @return a fully populated {@link GlobalConfigurationDTO} or {@code null} if input is {@code null}
     */
    public GlobalConfigurationDTO toDto(GlobalConfiguration entity) {
        if (entity == null) return null;
        GlobalConfigurationDTO dto = new GlobalConfigurationDTO();
        dto.setType(entity.getType());
        dto.setConfig(entity.getConfig());
        return dto;
    }

    /**
     * Converts an {@link GlobalConfigurationDTO} to an {@link GlobalConfiguration} entity.
     *
     * @param dto the {@link GlobalConfigurationDTO} to be converted; may be {@code null}
     * @return a fully populated {@link GlobalConfiguration} entity or {@code null} if input is {@code null}
     */
    public GlobalConfiguration toEntity(GlobalConfigurationDTO dto) {
        if (dto == null) return null;

        GlobalConfiguration entity = new GlobalConfiguration();
        entity.setType(dto.getType());
        entity.setConfig(dto.getConfig());

        return entity;
    }
}

