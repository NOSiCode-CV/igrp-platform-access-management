package cv.igrp.platform.access_management.users.mapper;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.IGRPBusinessUserDTO;
import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import org.springframework.stereotype.Component;

import java.util.Objects;

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
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setStatus(user.getStatus());
        dto.setPicture(user.getPicture());
        dto.setSignature(user.getSignature());
        // NIC falls back to username when no explicit NIC is recorded.
        dto.setNic(user.getNic() != null ? user.getNic() : user.getUsername());
        dto.setPhoneNumber(user.getPhoneNumber());
        return dto;
    }

    /**
     * Converts an {@link IGRPUserEntity} entity to an {@link IGRPBusinessUserDTO}.
     *
     * @param user the user entity to convert; may be {@code null}
     * @return a DTO representing the user for business purposes, or {@code null} if the input is {@code null}
     */
    public IGRPBusinessUserDTO toBusinessDTO(IGRPUserEntity user) {

        if (user == null) return null;
        IGRPBusinessUserDTO dto = new IGRPBusinessUserDTO();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setStatus(user.getStatus());
        dto.setPicture(user.getPicture());
        dto.setSignature(user.getSignature());
        dto.setExternalId(user.getId());
        dto.setNic(user.getNic());
        dto.setPhoneNumber(user.getPhoneNumber());
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
        user.setStatus(Objects.nonNull(dto.getStatus()) ? dto.getStatus() : Status.ACTIVE);
        user.setPicture(dto.getPicture());
        user.setSignature(dto.getSignature());
        // NIC defaults to the username when the DTO does not carry one.
        user.setNic(dto.getNic() != null ? dto.getNic() : dto.getUsername());
        user.setPhoneNumber(dto.getPhoneNumber());
        return user;
    }
}
