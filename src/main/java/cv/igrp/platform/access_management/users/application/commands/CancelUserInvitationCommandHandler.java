package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.notification.domain.service.InvitationNotificationSender;
import cv.igrp.platform.access_management.shared.application.constants.InvitationStatus;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.InvitationEntityRepository;
import cv.igrp.platform.access_management.users.mapper.InvitationMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cv.igrp.platform.access_management.shared.application.dto.InvitationDTO;

@Component
public class CancelUserInvitationCommandHandler implements CommandHandler<CancelUserInvitationCommand, ResponseEntity<InvitationDTO>> {

   private static final Logger LOGGER = LoggerFactory.getLogger(CancelUserInvitationCommandHandler.class);

   private final InvitationEntityRepository invitationRepository;
   private final InvitationMapper invitationMapper;
   private final InvitationNotificationSender invitationSender;

   public CancelUserInvitationCommandHandler(InvitationEntityRepository invitationRepository,
                                             InvitationMapper invitationMapper,
                                             InvitationNotificationSender invitationSender) {
      this.invitationRepository = invitationRepository;
      this.invitationMapper = invitationMapper;
      this.invitationSender = invitationSender;
   }

   @IgrpCommandHandler
   public ResponseEntity<InvitationDTO> handle(CancelUserInvitationCommand command) {
      // TODO(catalog-gap): publish UserInviteCancelledEvent (SettingsOperation.CANCEL_INVITE)
      // here once the settings-audit wiring for USERS-area actions is finalised. See roadmap.md.

      LOGGER.info("Cancelling invitation with id: {}", command.getId());

      var invitation = invitationRepository.findByIdOrThrow(command.getId());

      invitation.setStatus(InvitationStatus.CANCELED);

      var updatedInvitation = invitationRepository.save(invitation);

      LOGGER.info("Notifying new user: token={}, type={}, value={}", updatedInvitation.getToken(), updatedInvitation.getIdentifierType(), updatedInvitation.getIdentifierValue());

      if ("EMAIL".equalsIgnoreCase(updatedInvitation.getIdentifierType())) {
         // Primary path = Notification Service ("user-invitation-cancelled" template
         // by default — falls back to Spring Mail with the legacy
         // IGRP_MAIL_INVITE_CANCELLATION_TEMPLATE body when the template isn't
         // resolvable on the service side); fallback = legacy Spring Mail sender.
         invitationSender.sendCancellation(
                 updatedInvitation.getIdentifierValue(),
                 updatedInvitation.getIdentifierValue(),
                 updatedInvitation.getToken(),
                 null);
      }

      LOGGER.info("Invitation with id: {} cancelled successfully", command.getId());

      return ResponseEntity.ok(invitationMapper.toDto(updatedInvitation));

   }

}
