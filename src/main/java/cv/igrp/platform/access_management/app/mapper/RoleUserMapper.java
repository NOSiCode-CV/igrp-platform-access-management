package cv.igrp.platform.access_management.app.mapper;

import cv.igrp.platform.access_management.app.application.dto.RoleUserDTO;
import cv.igrp.platform.access_management.shared.domain.models.RoleUser;
import org.springframework.stereotype.Component;

@Component
public class RoleUserMapper {

    public RoleUserDTO toDto(RoleUser entity) {
        if (entity == null) return null;
        RoleUserDTO dto = new RoleUserDTO();
        dto.setId(entity.getId());
        dto.setUserId(entity.getUser().getId());
        dto.setRoleId(entity.getRole().getId());
        dto.setRoleName(entity.getRole().getName());
        dto.setAssignedAt(entity.getAssignedAt());
        return dto;
    }

    public RoleUser toEntity(RoleUserDTO dto) {
        if (dto == null) return null;
        RoleUser entity = new RoleUser();
        // Atenção: precisa setar as entidades completas de User e Role
        // Isso geralmente é feito por um service/repositório, então aqui apenas o ID é atribuído (ou como placeholder)

        // Exemplo de uso no service:
        // entity.setUser(userRepository.findById(dto.getUserId()).orElseThrow(...));

        return entity;
    }
}