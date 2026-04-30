package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.notifications.core.adapter.NotificationAdapter;
import cv.igrp.framework.notifications.core.exception.NotificationException;
import cv.igrp.framework.notifications.core.model.Notification;
import cv.igrp.framework.notifications.core.model.NotificationResult;
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

import java.util.Optional;

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
    private NotificationAdapter<NotificationResult> notificationAdapter;

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
    }

    @Test
    void testHandle_Success() throws NotificationException {
        when(invitationRepository.findByTokenAndStatusPending("valid-token")).thenReturn(invitation);
        when(otpEntityRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<OtpResponseDTO> response = commandHandler.handle(command);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("valid-token", response.getBody().getToken());
        assertTrue(response.getBody().getMessage().contains("OTP code has been sent"));

        verify(otpEntityRepository, times(1)).save(any());
        verify(notificationAdapter, times(1)).send(any(Notification.class));
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
    void testHandle_NotificationFailure() throws NotificationException {
        when(invitationRepository.findByTokenAndStatusPending("valid-token")).thenReturn(invitation);
        when(otpEntityRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("Mail server down")).when(notificationAdapter).send(any());

        IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class, () -> commandHandler.handle(command));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), exception.getBody().getStatus());
        assertTrue(exception.getMessage().contains("Failed to send OTP email"));
    }
}
