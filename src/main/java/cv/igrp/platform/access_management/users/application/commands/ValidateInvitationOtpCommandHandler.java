package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.dto.OtpResponseDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.OtpEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.OtpEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class ValidateInvitationOtpCommandHandler implements CommandHandler<ValidateInvitationOtpCommand, ResponseEntity<OtpResponseDTO>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValidateInvitationOtpCommandHandler.class);

    private final OtpEntityRepository otpEntityRepository;

    public ValidateInvitationOtpCommandHandler(OtpEntityRepository otpEntityRepository) {
        this.otpEntityRepository = otpEntityRepository;
    }

    @IgrpCommandHandler
    @Transactional
    public ResponseEntity<OtpResponseDTO> handle(ValidateInvitationOtpCommand command) {
        LOGGER.info("Validating OTP for token: {}", command.getToken());

        OtpEntity otpEntity = otpEntityRepository.findFirstByReferenceIdAndStatusOrderByCreatedAtDesc(command.getToken(), "PENDING")
                .orElseThrow(() -> IgrpResponseStatusException.of(HttpStatus.NOT_FOUND, "No pending OTP request found for this token"));

        if (LocalDateTime.now().isAfter(otpEntity.getExpiresAt())) {
            otpEntity.setStatus("EXPIRED");
            otpEntityRepository.save(otpEntity);
            throw IgrpResponseStatusException.of(HttpStatus.BAD_REQUEST, "The OTP code has expired. Please request a new one");
        }

        if (!command.getOtpCode().equals(otpEntity.getOtpCode())) {
            throw IgrpResponseStatusException.of(HttpStatus.BAD_REQUEST, "Invalid OTP code");
        }

        otpEntity.setStatus("APPROVED");
        otpEntityRepository.save(otpEntity);

        OtpResponseDTO response = new OtpResponseDTO();
        response.setToken(command.getToken());
        response.setMessage("OTP successfully validated.");

        return ResponseEntity.ok(response);
    }
}
