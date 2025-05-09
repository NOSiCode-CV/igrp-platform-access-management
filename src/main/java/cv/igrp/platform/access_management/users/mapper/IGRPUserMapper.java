package cv.igrp.platform.access_management.users.mapper;

import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import cv.igrp.platform.access_management.shared.domain.models.IGRPUser;
import org.springframework.stereotype.Component;

/**
 * Mapper component responsible for converting between {@link IGRPUser} entities
 * and {@link IGRPUserDTO} data transfer objects.
 * <p>
 * This mapper provides bidirectional conversion methods to support data transformation
 * between the domain layer and the application/presentation layers.
 * </p>
 */
@Component
public class IGRPUserMapper {

    /**
     * Converts an {@link IGRPUser} entity to an {@link IGRPUserDTO}.
     *
     * @param user the user entity to convert; may be {@code null}
     * @return a DTO representing the user, or {@code null} if the input is {@code null}
     */
    public IGRPUserDTO toDto(IGRPUser user) {
        if (user == null) return null;
        IGRPUserDTO dto = new IGRPUserDTO();
        dto.setId(Integer.parseInt(user.getId()));
        dto.setName(user.getName());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        return dto;
    }

    /**
     * Converts an {@link IGRPUserDTO} to an {@link IGRPUser} entity.
     *
     * @param dto the DTO to convert; may be {@code null}
     * @return a new user entity populated with data from the DTO,
     *         or {@code null} if the input is {@code null}
     */
    public IGRPUser toEntity(IGRPUserDTO dto) {
        if (dto == null) return null;
        IGRPUser user = new IGRPUser();
        user.setId(dto.getId());
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setName(dto.getName());
        return user;
    }
}
