package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.notifications.core.adapter.NotificationAdapter;
import cv.igrp.framework.notifications.core.model.Notification;
import cv.igrp.framework.notifications.core.model.NotificationResult;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.constants.InvitationStatus;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.InvitationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.utils.UserUtils;
import cv.igrp.platform.access_management.users.mapper.InvitationMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cv.igrp.platform.access_management.shared.application.dto.InvitationDTO;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class ResendUserInvitationCommandHandler implements CommandHandler<ResendUserInvitationCommand, ResponseEntity<InvitationDTO>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResendUserInvitationCommandHandler.class);

    @Value("${igrp.mail.invite.template}")
    private String emailTemplate = """
            Dear {{user}}, your were invited to the iGRP platform
            
            Please click on the link below to accept the invitation:
            {{url}}
            
            Best Regards.
            iGRP
            """;

    @Value("${igrp.app-center.url:}")
    private String appCenterUrl = "";

    private final NotificationAdapter<NotificationResult> notificationAdapter;
    private final InvitationEntityRepository invitationRepository;
    private final UserUtils userUtils;
    private final InvitationMapper invitationMapper;

    public ResendUserInvitationCommandHandler(
            NotificationAdapter<NotificationResult> notificationAdapter,
            InvitationEntityRepository invitationRepository,
            UserUtils userUtils,
            InvitationMapper invitationMapper
    ) {
        this.notificationAdapter = notificationAdapter;
        this.invitationRepository = invitationRepository;
        this.userUtils = userUtils;
        this.invitationMapper = invitationMapper;
    }

    @IgrpCommandHandler
    public ResponseEntity<InvitationDTO> handle(ResendUserInvitationCommand command) {

        var invitation = invitationRepository.findByIdOrThrow(command.getId());

        var newToken = UUID.randomUUID().toString();

        var url = userUtils.constructInvitationUrl(appCenterUrl, newToken);

        LOGGER.info("Inviting new user: token={}, type={}, value={}", newToken, invitation.getIdentifierType(), invitation.getIdentifierValue());

        invitation.setToken(newToken);

        if (!Objects.equals(invitation.getStatus(), InvitationStatus.PENDING)) {
            invitation.setStatus(InvitationStatus.PENDING);
        }

        var updatedInvitation = invitationRepository.save(invitation);

        try {

            if ("EMAIL".equalsIgnoreCase(invitation.getIdentifierType())) {
                var notification = new Notification();
    
                notification.setRecipients(List.of(invitation.getIdentifierValue()));
                notification.setSubject("iGRP User Invitation");
                notification.setContent(emailTemplate.replace("{{user}}", invitation.getIdentifierValue()).replace("{{url}}", url));
                notification.setMetadata(Map.of("invitationToken", newToken, "email", invitation.getIdentifierValue()));
    
                notificationAdapter.send(notification);
            }

        } catch (Exception e) {
            LOGGER.error("Error while sending user invitation", e);
        }

        LOGGER.info("User invited successfully with token={}", newToken);

        return ResponseEntity.ok(invitationMapper.toDtoWithUrl(updatedInvitation, url));

    }

}