package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.notifications.core.adapter.NotificationAdapter;
import cv.igrp.framework.notifications.core.model.Notification;
import cv.igrp.framework.notifications.core.model.NotificationResult;
import cv.igrp.platform.access_management.shared.application.constants.InvitationStatus;
import cv.igrp.platform.access_management.shared.application.dto.InvitationDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.InvitationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.InvitationEntityRepository;
import cv.igrp.platform.access_management.users.mapper.InvitationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CancelUserInvitationCommandHandlerTest {

    @Mock private InvitationEntityRepository invitationRepository;
    @Mock private InvitationMapper invitationMapper;
    @Mock private NotificationAdapter<NotificationResult> notificationAdapter;

    private CancelUserInvitationCommandHandler handler;
    private InvitationEntity invitation;
    private CancelUserInvitationCommand command;

    @BeforeEach
    void setUp() {
        handler = new CancelUserInvitationCommandHandler(invitationRepository, invitationMapper, notificationAdapter);
        invitation = new InvitationEntity();
        invitation.setId(10);
        invitation.setToken("tok");
        invitation.setIdentifierType("EMAIL");
        invitation.setIdentifierValue("a@b.cv");
        command = new CancelUserInvitationCommand();
        command.setId(10);
    }

    @Test
    void handle_CancelsAndNotifies() throws Exception {
        when(invitationRepository.findByIdOrThrow(10)).thenReturn(invitation);
        when(invitationRepository.save(invitation)).thenReturn(invitation);
        when(invitationMapper.toDto(invitation)).thenReturn(new InvitationDTO());

        ResponseEntity<InvitationDTO> resp = handler.handle(command);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(InvitationStatus.CANCELED, invitation.getStatus());
        verify(notificationAdapter).send(any(Notification.class));
    }

    @Test
    void handle_NonEmailIdentifier_SkipsNotification() {
        invitation.setIdentifierType("PHONE");
        when(invitationRepository.findByIdOrThrow(10)).thenReturn(invitation);
        when(invitationRepository.save(invitation)).thenReturn(invitation);
        when(invitationMapper.toDto(invitation)).thenReturn(new InvitationDTO());

        handler.handle(command);

        verifyNoInteractions(notificationAdapter);
    }

    @Test
    void handle_NotificationFails_StillCancels() throws Exception {
        when(invitationRepository.findByIdOrThrow(10)).thenReturn(invitation);
        when(invitationRepository.save(invitation)).thenReturn(invitation);
        when(invitationMapper.toDto(invitation)).thenReturn(new InvitationDTO());
        doThrow(new RuntimeException("smtp down")).when(notificationAdapter).send(any(Notification.class));

        ResponseEntity<InvitationDTO> resp = handler.handle(command);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(InvitationStatus.CANCELED, invitation.getStatus());
    }
}
