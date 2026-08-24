package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.notification.domain.service.InvitationNotificationSender;
import cv.igrp.platform.access_management.shared.application.dto.OtpResponseDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpErrorCode;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.OtpEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.InvitationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.OtpEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;

@Component
public class ValidateInvitationEmailCommandHandler implements CommandHandler<ValidateInvitationEmailCommand, ResponseEntity<OtpResponseDTO>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValidateInvitationEmailCommandHandler.class);

    private final InvitationEntityRepository invitationRepository;
    private final OtpEntityRepository otpEntityRepository;
    private final InvitationNotificationSender invitationSender;

    public ValidateInvitationEmailCommandHandler(InvitationEntityRepository invitationRepository,
                                                 OtpEntityRepository otpEntityRepository,
                                                 InvitationNotificationSender invitationSender) {
        this.invitationRepository = invitationRepository;
        this.otpEntityRepository = otpEntityRepository;
        this.invitationSender = invitationSender;
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

        // Primary = Notification Service ("user-invitation-otp" template by default —
        // not yet seeded, so this will fall back to Spring Mail with the legacy
        // IGRP_MAIL_OTP_TEMPLATE body until the notification team publishes it);
        // fallback = legacy Spring Mail sender. Unlike the other invitation events
        // this one MUST surface failure to the caller — an OTP that never arrives
        // blocks the invitation flow entirely, so use the OrThrow variant.
        boolean delivered = invitationSender.sendOtpOrThrow(
                invitation.getIdentifierValue(),
                otpCode,
                command.getToken(),
                null);
        if (!delivered) {
            LOGGER.error("Failed to send OTP via email for invitationToken={}", command.getToken());
            throw IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_INVITATION_OTP_SEND_FAILED);
        }
        LOGGER.info("OTP sent to email: {}", invitation.getIdentifierValue());

        OtpResponseDTO response = new OtpResponseDTO();
        response.setToken(command.getToken());
        response.setMessage("OTP code has been sent to your email.");

        return ResponseEntity.ok(response);
    }
}
