package cv.igrp.platform.access_management.users.mapper;

import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import org.springframework.stereotype.Component;

/**
 * Mapper component responsible for converting between {@link IGRPUserEntity} entities
 * and {@link IGRPUserDTO} data transfer objects.
 * <p>
 * This mapper provides bidirectional conversion methods to support data transformation
 * between the domain layer and the application/presentation layers.
 * </p>
 */
@Component
public class IGRPUserMapper {

    /**
     * Converts an {@link IGRPUserEntity} entity to an {@link IGRPUserDTO}.
     *
     * @param user the user entity to convert; may be {@code null}
     * @return a DTO representing the user, or {@code null} if the input is {@code null}
     */
    public IGRPUserDTO toDto(IGRPUserEntity user) {
        if (user == null) return null;
        IGRPUserDTO dto = new IGRPUserDTO();
        dto.setId(Integer.parseInt(user.getId()));
        dto.setName(user.getName());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setPicture(user.getPicture());
        dto.setSignature(user.getSignature());
        return dto;
    }

    /**
     * Converts an {@link IGRPUserDTO} to an {@link IGRPUserEntity} entity.
     *
     * @param dto the DTO to convert; may be {@code null}
     * @return a new user entity populated with data from the DTO,
     *         or {@code null} if the input is {@code null}
     */
    public IGRPUserEntity toEntity(IGRPUserDTO dto) {
        if (dto == null) return null;
        IGRPUserEntity user = new IGRPUserEntity();
        user.setId(dto.getId());
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setName(dto.getName());
        user.setPicture(dto.getPicture());
        user.setSignature(dto.getSignature());
        return user;
    }
}
