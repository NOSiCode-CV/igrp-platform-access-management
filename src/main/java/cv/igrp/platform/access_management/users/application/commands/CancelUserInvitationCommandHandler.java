package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.notifications.core.adapter.NotificationAdapter;
import cv.igrp.framework.notifications.core.model.Notification;
import cv.igrp.framework.notifications.core.model.NotificationResult;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.constants.InvitationStatus;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.InvitationEntityRepository;
import cv.igrp.platform.access_management.users.mapper.InvitationMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cv.igrp.platform.access_management.shared.application.dto.InvitationDTO;

import java.util.List;
import java.util.Map;
import cv.igrp.platform.access_management.shared.application.constants.IdentifierType;

@Component
public class CancelUserInvitationCommandHandler implements CommandHandler<CancelUserInvitationCommand, ResponseEntity<InvitationDTO>> {

   private static final Logger LOGGER = LoggerFactory.getLogger(CancelUserInvitationCommandHandler.class);

   @Value("${igrp.mail.invite.cancellation.template}")
   private String emailTemplate = """
                        Dear {{user}}, your invitation to the iGRP platform was cancelled
                        
                        Best Regards.
                        iGRP
                        """;

   private final InvitationEntityRepository invitationRepository;
   private final InvitationMapper invitationMapper;
   private final NotificationAdapter<NotificationResult> notificationAdapter;

   public CancelUserInvitationCommandHandler(InvitationEntityRepository invitationRepository, InvitationMapper invitationMapper, NotificationAdapter<NotificationResult> notificationAdapter) {
      this.invitationRepository = invitationRepository;
      this.invitationMapper = invitationMapper;
      this.notificationAdapter = notificationAdapter;
   }

   @IgrpCommandHandler
   public ResponseEntity<InvitationDTO> handle(CancelUserInvitationCommand command) {

      LOGGER.info("Cancelling invitation with id: {}", command.getId());

      var invitation = invitationRepository.findByIdOrThrow(command.getId());

      invitation.setStatus(InvitationStatus.CANCELED);

      var updatedInvitation = invitationRepository.save(invitation);

      try {

         LOGGER.info("Notifying new user: token={}, type={}, value={}", updatedInvitation.getToken(), updatedInvitation.getIdentifierType(), updatedInvitation.getIdentifierValue());

         if (IdentifierType.EMAIL.equals(updatedInvitation.getIdentifierType())) {
             var notification = new Notification();

             notification.setRecipients(List.of(updatedInvitation.getIdentifierValue()));
             notification.setSubject("iGRP User Invitation");
             notification.setContent(emailTemplate.replace("{{user}}", updatedInvitation.getIdentifierValue()));
             notification.setMetadata(Map.of("invitationToken", updatedInvitation.getToken(), "email", updatedInvitation.getIdentifierValue()));

             notificationAdapter.send(notification);
         }

      } catch (Exception e) {
         LOGGER.error("Notification Email failed", e);
      }

      LOGGER.info("Invitation with id: {} cancelled successfully", command.getId());

      return ResponseEntity.ok(invitationMapper.toDto(updatedInvitation));

   }

}