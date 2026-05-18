package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.notifications.core.adapter.NotificationAdapter;
import cv.igrp.framework.notifications.core.model.Notification;
import cv.igrp.framework.notifications.core.model.NotificationResult;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.dto.OtpResponseDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpErrorCode;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.OtpEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.InvitationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.OtpEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Component
public class ValidateInvitationEmailCommandHandler implements CommandHandler<ValidateInvitationEmailCommand, ResponseEntity<OtpResponseDTO>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValidateInvitationEmailCommandHandler.class);

    private final InvitationEntityRepository invitationRepository;
    private final OtpEntityRepository otpEntityRepository;
    private final NotificationAdapter<NotificationResult> notificationAdapter;

    @Value("${igrp.mail.otp.template:Dear user, your OTP code is {{otp}}.}")
    private String emailTemplate;

    public ValidateInvitationEmailCommandHandler(InvitationEntityRepository invitationRepository,
                                                 OtpEntityRepository otpEntityRepository,
                                                 NotificationAdapter<NotificationResult> notificationAdapter) {
        this.invitationRepository = invitationRepository;
        this.otpEntityRepository = otpEntityRepository;
        this.notificationAdapter = notificationAdapter;
    }

    @IgrpCommandHandler
    @Transactional
    public ResponseEntity<OtpResponseDTO> handle(ValidateInvitationEmailCommand command) {
        LOGGER.info("Validating email for invitation token: {}", command.getToken());

        var invitation = invitationRepository.findByTokenAndStatusPending(command.getToken());

        if (!command.getEmail().equalsIgnoreCase(invitation.getIdentifierValue())) {
            LOGGER.warn("Email validation failed. Provided: {}, Expected: {}", command.getEmail(), invitation.getIdentifierValue());
            throw IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_INVITATION_EMAIL_MISMATCH);
        }

        // Generate 6-digit OTP
        String otpCode = String.format("%06d", new Random().nextInt(999999));

        OtpEntity otpEntity = new OtpEntity();
        otpEntity.setReferenceId(command.getToken());
        otpEntity.setOtpCode(otpCode);
        otpEntity.setStatus("PENDING");
        otpEntity.setExpiresAt(LocalDateTime.now().plusMinutes(10));

        OtpEntity savedOtp = otpEntityRepository.save(otpEntity);

        try {
            var notification = new Notification();
            notification.setRecipients(List.of(invitation.getIdentifierValue()));
            notification.setSubject("iGRP Security Code");
            notification.setContent(emailTemplate.replace("{{otp}}", otpCode));
            notification.setMetadata(Map.of("invitationToken", command.getToken(), "email", invitation.getIdentifierValue()));

            notificationAdapter.send(notification);
            LOGGER.info("OTP sent to email: {}", invitation.getIdentifierValue());
        } catch (Exception e) {
            LOGGER.error("Failed to send OTP via email", e);
            throw IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_INVITATION_OTP_SEND_FAILED);
        }

        OtpResponseDTO response = new OtpResponseDTO();
        response.setToken(command.getToken());
        response.setMessage("OTP code has been sent to your email.");

        return ResponseEntity.ok(response);
    }
}
