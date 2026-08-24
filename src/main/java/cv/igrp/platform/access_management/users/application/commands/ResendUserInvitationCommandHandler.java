package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.notification.domain.service.InvitationNotificationSender;
import cv.igrp.platform.access_management.shared.application.constants.InvitationStatus;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.InvitationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.utils.UserUtils;
import cv.igrp.platform.access_management.users.mapper.InvitationMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cv.igrp.platform.access_management.shared.application.dto.InvitationDTO;

import java.util.Objects;
import java.util.UUID;

@Component
public class ResendUserInvitationCommandHandler implements CommandHandler<ResendUserInvitationCommand, ResponseEntity<InvitationDTO>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResendUserInvitationCommandHandler.class);

    @Value("${igrp.app-center.url:}")
    private String appCenterUrl = "";

    private final InvitationNotificationSender invitationSender;
    private final InvitationEntityRepository invitationRepository;
    private final UserUtils userUtils;
    private final InvitationMapper invitationMapper;

    public ResendUserInvitationCommandHandler(
            InvitationNotificationSender invitationSender,
            InvitationEntityRepository invitationRepository,
            UserUtils userUtils,
            InvitationMapper invitationMapper
    ) {
        this.invitationSender = invitationSender;
        this.invitationRepository = invitationRepository;
        this.userUtils = userUtils;
        this.invitationMapper = invitationMapper;
    }

    @IgrpCommandHandler
    public ResponseEntity<InvitationDTO> handle(ResendUserInvitationCommand command) {
        // TODO(catalog-gap): publish UserInviteResentEvent (SettingsOperation.RESEND_INVITE)
        // here once the settings-audit wiring for USERS-area actions is finalised. See roadmap.md.

        var invitation = invitationRepository.findByIdOrThrow(command.getId());

        var newToken = UUID.randomUUID().toString();

        var url = userUtils.constructInvitationUrl(appCenterUrl, newToken);

        LOGGER.info("Inviting new user: token={}, type={}, value={}", newToken, invitation.getIdentifierType(), invitation.getIdentifierValue());

        invitation.setToken(newToken);

        if (!Objects.equals(invitation.getStatus(), InvitationStatus.PENDING)) {
            invitation.setStatus(InvitationStatus.PENDING);
        }

        var updatedInvitation = invitationRepository.save(invitation);

        if ("EMAIL".equalsIgnoreCase(invitation.getIdentifierType())) {
            // Primary path = Notification Service ("user-invitation" template by default,
            // configurable via igrp.notification.service.invitation.resend-template-code);
            // fallback = legacy Spring Mail sender. The sender swallows any failure so
            // the invite is still persisted + returned even if all email paths are down.
            invitationSender.sendResend(
                    invitation.getIdentifierValue(),
                    invitation.getIdentifierValue(),
                    url,
                    newToken,
                    null);
        }

        LOGGER.info("User invited successfully with token={}", newToken);

        return ResponseEntity.ok(invitationMapper.toDtoWithUrl(updatedInvitation, url));

    }

}
