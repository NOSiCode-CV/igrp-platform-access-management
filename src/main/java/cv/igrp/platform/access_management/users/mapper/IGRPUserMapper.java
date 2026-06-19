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
        // Expose NIC as stored, including null. The previous
        // `user.getNic() != null ? user.getNic() : user.getUsername()` fallback
        // surfaced the user's username (often the JWT sub UUID, sometimes the
        // email) as if it were the NIC, leaking incorrect identity data on every
        // GET response and confusing clients that decided NIC presence based on
        // the response. NIC is only populated by authoritative IdP claims (CMDCV
        // / CNI) or explicit user updates; absence is meaningful and should be
        // represented as null.
        dto.setNic(user.getNic());
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
        // Persist NIC only when the DTO actually carries a value. The previous
        // `dto.getNic() != null ? dto.getNic() : dto.getUsername()` substitution
        // wrote the username (typically the JWT sub UUID or, for password-auth
        // users, the email) into the NIC column whenever the request body
        // omitted nic — corrupting storage and making NIC-based lookups
        // (findByNicIgnoreCase, findByAnyIdentifier) match users by their
        // username instead. NIC is optional and may legitimately be null:
        // either the IdP supplied it (CMDCV / CNI) or the user supplied it
        // through the request body; otherwise it stays null.
        if (dto.getNic() != null && !dto.getNic().isBlank()) {
            user.setNic(dto.getNic());
        }
        user.setPhoneNumber(dto.getPhoneNumber());
        return user;
    }
}
