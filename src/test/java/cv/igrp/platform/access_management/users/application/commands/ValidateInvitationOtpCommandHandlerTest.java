package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.platform.access_management.shared.application.dto.OtpResponseDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.OtpEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.OtpEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ValidateInvitationOtpCommandHandler.class)
class ValidateInvitationOtpCommandHandlerTest {

    @MockBean
    private OtpEntityRepository otpEntityRepository;

    @Autowired
    private ValidateInvitationOtpCommandHandler commandHandler;

    private ValidateInvitationOtpCommand command;
    private OtpEntity otpEntity;

    @BeforeEach
    void setUp() {
        command = new ValidateInvitationOtpCommand();
        command.setToken("valid-token");
        command.setOtpCode("123456");

        otpEntity = new OtpEntity();
        otpEntity.setReferenceId("valid-token");
        otpEntity.setOtpCode("123456");
        otpEntity.setStatus("PENDING");
        otpEntity.setExpiresAt(LocalDateTime.now().plusMinutes(10));
    }

    @Test
    void testHandle_Success() {
        when(otpEntityRepository.findFirstByReferenceIdAndStatusOrderByCreatedAtDesc("valid-token", "PENDING"))
                .thenReturn(Optional.of(otpEntity));
        when(otpEntityRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<OtpResponseDTO> response = commandHandler.handle(command);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("APPROVED", otpEntity.getStatus());
        assertEquals("OTP successfully validated.", response.getBody().getMessage());

        verify(otpEntityRepository, times(1)).save(otpEntity);
    }

    @Test
    void testHandle_OtpNotFound() {
        when(otpEntityRepository.findFirstByReferenceIdAndStatusOrderByCreatedAtDesc("valid-token", "PENDING"))
                .thenReturn(Optional.empty());

        IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class, () -> commandHandler.handle(command));
        assertEquals(HttpStatus.NOT_FOUND.value(), exception.getBody().getStatus());
        assertTrue(exception.getMessage().contains("No pending OTP request found"));
    }

    @Test
    void testHandle_OtpExpired() {
        otpEntity.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(otpEntityRepository.findFirstByReferenceIdAndStatusOrderByCreatedAtDesc("valid-token", "PENDING"))
                .thenReturn(Optional.of(otpEntity));

        IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class, () -> commandHandler.handle(command));
        assertEquals(HttpStatus.BAD_REQUEST.value(), exception.getBody().getStatus());
        assertEquals("EXPIRED", otpEntity.getStatus());
        assertTrue(exception.getMessage().contains("The OTP code has expired"));
    }

    @Test
    void testHandle_InvalidOtpCode() {
        command.setOtpCode("wrong-code");
        when(otpEntityRepository.findFirstByReferenceIdAndStatusOrderByCreatedAtDesc("valid-token", "PENDING"))
                .thenReturn(Optional.of(otpEntity));

        IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class, () -> commandHandler.handle(command));
        assertEquals(HttpStatus.BAD_REQUEST.value(), exception.getBody().getStatus());
        assertTrue(exception.getMessage().contains("Invalid OTP code"));
    }
}
