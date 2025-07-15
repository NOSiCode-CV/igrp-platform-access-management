package cv.igrp.platform.access_management.department.mapper;

import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import org.springframework.stereotype.Component;

import java.util.Objects;

import static java.util.Optional.ofNullable;

/**
 * Mapper responsible for converting between {@link DepartmentEntity} entities and {@link DepartmentDTO} data transfer objects.
 * <p>
 * Handles both directions of conversion: entity to DTO and DTO to entity.
 * Also provides a method to update an existing {@link DepartmentEntity} entity from a {@link DepartmentDTO}.
 * </p>
 *
 * <p><strong>Mapping behavior:</strong></p>
 * <ul>
 *   <li>When mapping to DTO, it extracts the IDs of associated {@link ApplicationEntity} and parent {@link DepartmentEntity}.</li>
 *   <li>When mapping to entity, it defaults status to {@code DepartmentStatus.ACTIVE} if not provided.</li>
 * </ul>
 *
 * @see DepartmentEntity
 * @see DepartmentDTO
 * @see DepartmentStatus
 */
@Component
public class DepartmentMapper {

    /**
     * Converts a {@link DepartmentEntity} entity to a {@link DepartmentDTO}.
     *
     * @param department the entity to convert
     * @return the corresponding DTO, or {@code null} if the entity is {@code null}
     */
    public DepartmentDTO toDto(DepartmentEntity department) {
        if (department == null) return null;
        DepartmentDTO dto = new DepartmentDTO();
        dto.setId(department.getId());
        dto.setCode(department.getCode());
        dto.setName(department.getName());
        dto.setStatus(department.getStatus());
        dto.setDescription(department.getDescription());
        dto.setParent_id(ofNullable(department.getParentId()).map(DepartmentEntity::getId).orElse(null));
        return dto;
    }

    /**
     * Converts a {@link DepartmentDTO} to a new {@link DepartmentEntity} entity.
     *
     * @param dto the DTO to convert
     * @return the corresponding entity, or {@code null} if the DTO is {@code null}
     */
    public DepartmentEntity toEntity(DepartmentDTO dto) {
        if (dto == null) return null;
        DepartmentEntity department = new DepartmentEntity();
        department.setId(dto.getId());
        department.setCode(dto.getCode());
        department.setName(dto.getName());
        department.setStatus(Objects.nonNull(dto.getStatus()) ? dto.getStatus() : DepartmentStatus.ACTIVE);
        department.setDescription(dto.getDescription());
        return department;
    }

    /**
     * Updates an existing {@link DepartmentEntity} entity using data from a {@link DepartmentDTO}.
     *
     * @param dto the source DTO
     * @param department the target entity to update
     */
    public void updateEntityFromDto(DepartmentDTO dto, DepartmentEntity department) {
        department.setCode(dto.getCode());
        department.setName(dto.getName());
        department.setStatus(dto.getStatus());
        department.setDescription(dto.getDescription());
    }
}