package cv.igrp.platform.access_management.users.mapper;

import cv.igrp.platform.access_management.shared.application.dto.CodeDescriptionDTO;
import cv.igrp.platform.access_management.shared.application.dto.InvitationDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.InvitationEntity;
import org.springframework.stereotype.Component;
import java.util.ArrayList;

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
        dto.setIdentifierType(invitation.getIdentifierType());
        dto.setIdentifierValue(invitation.getIdentifierValue());
        dto.setStatus(invitation.getStatus());
        dto.setInvitationDate(invitation.getCreatedDate());
        dto.setExpiry(invitation.getExpiry());
        dto.setComments(invitation.getComments());
        dto.setInvitedBy(invitation.getCreatedBy());
        dto.setRoles(invitation.getRoles().stream().map(it -> {
            var output = new CodeDescriptionDTO();
            output.setCode(it.getCode());
            output.setDescription(it.getName());
            return output;
        }).toList());
        invitation.getRoles().stream().findFirst().ifPresent(it -> dto.setDepartment(new CodeDescriptionDTO(it.getDepartment().getCode(), it.getDepartment().getName())));
        return dto;
    }

    public InvitationDTO toDtoWithUrl(InvitationEntity invitation, String url) {
        if(invitation == null) return null;
        InvitationDTO dto = toDto(invitation);
        dto.setInvitationUrl(url);
        return dto;
    }

}
