package cv.igrp.platform.access_management.users.mapper;

import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import cv.igrp.platform.access_management.shared.domain.models.IGRPUser;
import org.springframework.stereotype.Component;

@Component
public class IGRPUserMapper {

    public IGRPUserDTO toDto(IGRPUser user) {
        if (user == null) return null;
        IGRPUserDTO dto = new IGRPUserDTO();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        return dto;
    }

    public IGRPUser toEntity(IGRPUserDTO dto) {
        if (dto == null) return null;
        IGRPUser user = new IGRPUser();
        user.setId(dto.getId());
        //user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        //user.setStatus(dto.getStatus());
        user.setName(dto.getName());
        return user;
    }
}
