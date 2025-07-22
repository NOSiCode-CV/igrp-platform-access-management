package cv.igrp.platform.access_management.department.mapper;

import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import org.springframework.stereotype.Component;

@Component
public class DepartmentMapper {

    public DepartmentDTO toDto(DepartmentEntity entity) {
        if (entity == null) return null;

        DepartmentDTO dto = new DepartmentDTO();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setStatus(entity.getStatus());
        if (entity.getParentId() != null) {
            dto.setParent_code(entity.getParentId().getCode());
        }
        return dto;
    }

    public DepartmentEntity toEntity(DepartmentDTO dto) {
        if (dto == null) return null;

        DepartmentEntity entity = new DepartmentEntity();
        entity.setId(dto.getId());
        entity.setCode(dto.getCode());
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : DepartmentStatus.ACTIVE);
        return entity;
    }

    public void updateEntityFromDto(DepartmentDTO dto, DepartmentEntity entity) {
        if (dto == null || entity == null) return;

        entity.setCode(dto.getCode());
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : DepartmentStatus.ACTIVE);
    }
}
