package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.notifications.core.exception.NotificationException;
import cv.igrp.platform.access_management.notification.domain.service.InvitationNotificationSender;
import cv.igrp.platform.access_management.shared.config.NotificationServiceProperties;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.InvitationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.InvitationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.OtpEntityRepository;
import cv.igrp.platform.access_management.shared.application.dto.OtpResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.OtpEntity;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ValidateInvitationEmailCommandHandler.class)
class ValidateInvitationEmailCommandHandlerTest {

    @MockBean
    private InvitationEntityRepository invitationRepository;

    @MockBean
    private OtpEntityRepository otpEntityRepository;

    @MockBean
    private InvitationNotificationSender invitationSender;

    @MockBean
    private NotificationServiceProperties notificationProperties;

    @Autowired
    private ValidateInvitationEmailCommandHandler commandHandler;

    private ValidateInvitationEmailCommand command;
    private InvitationEntity invitation;

    @BeforeEach
    void setUp() {
        command = new ValidateInvitationEmailCommand();
        command.setToken("valid-token");
        command.setEmail("user@example.com");

        invitation = new InvitationEntity();
        invitation.setToken("valid-token");
        invitation.setIdentifierValue("user@example.com");

        // Wire the default locale + default OTP TTL so the handler passes them through explicitly.
        NotificationServiceProperties.Invitation invProps = new NotificationServiceProperties.Invitation();
        invProps.setDefaultLocale("pt");
        // Keep the Invitation defaults for otpTtl (10 minutes) unless a test overrides it.
        when(notificationProperties.getInvitation()).thenReturn(invProps);
    }

    @Test
    void testHandle_Success() throws NotificationException {
        when(invitationRepository.findByTokenAndStatusPending("valid-token")).thenReturn(invitation);
        when(otpEntityRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(invitationSender.sendOtpOrThrow(any(), any(), any(), any(), any())).thenReturn(true);

        ResponseEntity<OtpResponseDTO> response = commandHandler.handle(command);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("valid-token", response.getBody().getToken());
        assertTrue(response.getBody().getMessage().contains("OTP code has been sent"));

        verify(otpEntityRepository, times(1)).save(any());
        verify(invitationSender, times(1)).sendOtpOrThrow(
                eq("user@example.com"),
                eq("user@example.com"),   // userDisplayName defaults to the invitation identifier
                any(String.class),
                eq("valid-token"),
                eq("pt"));                // inherited from NotificationServiceProperties.invitation.default-locale
    }

    @Test
    void testHandle_EmailMismatch() {
        command.setEmail("wrong@example.com");
        when(invitationRepository.findByTokenAndStatusPending("valid-token")).thenReturn(invitation);

        IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class, () -> commandHandler.handle(command));
        assertEquals(HttpStatus.BAD_REQUEST.value(), exception.getBody().getStatus());
        assertTrue(exception.getMessage().contains("provided email does not match"));
    }

    @Test
    void testHandle_OtpExpiresAt_UsesConfiguredTtl() throws NotificationException {
        // Override the invitation props to a short TTL and assert the persisted
        // OtpEntity.expiresAt reflects it (rather than the hard-coded 10 minutes).
        NotificationServiceProperties.Invitation invProps = new NotificationServiceProperties.Invitation();
        invProps.setDefaultLocale("pt");
        invProps.setOtpTtl(Duration.ofMinutes(2));
        when(notificationProperties.getInvitation()).thenReturn(invProps);

        when(invitationRepository.findByTokenAndStatusPending("valid-token")).thenReturn(invitation);
        when(invitationSender.sendOtpOrThrow(any(), any(), any(), any(), any())).thenReturn(true);

        ArgumentCaptor<OtpEntity> captor = ArgumentCaptor.forClass(OtpEntity.class);
        when(otpEntityRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        LocalDateTime before = LocalDateTime.now();
        commandHandler.handle(command);
        LocalDateTime after = LocalDateTime.now();

        LocalDateTime expiresAt = captor.getValue().getExpiresAt();
        assertNotNull(expiresAt);
        // Should fall inside [now + 2m - epsilon, now + 2m + epsilon].
        LocalDateTime lowerBound = before.plusMinutes(2).minus(1, ChronoUnit.SECONDS);
        LocalDateTime upperBound = after.plusMinutes(2).plus(1, ChronoUnit.SECONDS);
        assertFalse(expiresAt.isBefore(lowerBound), "expiresAt " + expiresAt + " is before " + lowerBound);
        assertFalse(expiresAt.isAfter(upperBound),  "expiresAt " + expiresAt + " is after "  + upperBound);
    }

    @Test
    void testHandle_NotificationFailure() throws NotificationException {
        when(invitationRepository.findByTokenAndStatusPending("valid-token")).thenReturn(invitation);
        when(otpEntityRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        // sendOtpOrThrow returns false when both primary + fallback fail — the
        // handler translates that to the IGRP_AUTH_INVITATION_OTP_SEND_FAILED
        // business error so the caller receives 5xx rather than a silent success.
        when(invitationSender.sendOtpOrThrow(any(), any(), any(), any(), any())).thenReturn(false);

        IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class, () -> commandHandler.handle(command));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), exception.getBody().getStatus());
        assertTrue(exception.getMessage().contains("Failed to send OTP email"));
    }
}
