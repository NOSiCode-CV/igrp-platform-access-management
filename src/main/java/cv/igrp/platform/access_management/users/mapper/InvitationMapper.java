package cv.igrp.platform.access_management.users.mapper;

import cv.igrp.platform.access_management.shared.application.dto.InvitationDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.InvitationEntity;
import org.springframework.stereotype.Component;

/**
 * Mapper for {@link InvitationEntity}
 */
@Component
public class InvitationMapper {

    /**
     * Converts an {@link InvitationEntity} to an {@link InvitationEntity}
     *
     * @param invitation the invitation entity to convert
     * @return the converted invitation
     */
    public InvitationDTO toDto(InvitationEntity invitation) {
        if(invitation == null) return null;
        InvitationDTO dto = new InvitationDTO();
        dto.setId(invitation.getId());
        dto.setEmail(invitation.getEmail());
        dto.setStatus(invitation.getStatus());
        dto.setInvitationDate(invitation.getCreatedDate());
        dto.setExpiry(invitation.getExpiry());
        dto.setComments(invitation.getComments());
        dto.setInvitedBy(invitation.getCreatedBy());
        return dto;
    }

    public InvitationDTO toDtoWithUrl(InvitationEntity invitation, String url) {
        if(invitation == null) return null;
        InvitationDTO dto = toDto(invitation);
        dto.setInvitationUrl(url);
        return dto;
    }

}
