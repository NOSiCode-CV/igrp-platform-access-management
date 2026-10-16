package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.platform.access_management.notification.domain.service.InvitationNotificationSender;
import cv.igrp.platform.access_management.shared.application.constants.InvitationStatus;
import cv.igrp.platform.access_management.shared.application.dto.InvitationDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.InvitationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.InvitationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.utils.UserUtils;
import cv.igrp.platform.access_management.users.mapper.InvitationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ResendUserInvitationCommandHandlerTest {

    @Mock private InvitationNotificationSender invitationSender;
    @Mock private InvitationEntityRepository invitationRepository;
    @Mock private UserUtils userUtils;
    @Mock private InvitationMapper invitationMapper;

    private ResendUserInvitationCommandHandler handler;
    private InvitationEntity invitation;

    @BeforeEach
    void setUp() {
        handler = new ResendUserInvitationCommandHandler(invitationSender, invitationRepository, userUtils, invitationMapper);
        invitation = new InvitationEntity();
        invitation.setId(7);
        invitation.setToken("old-token");
        invitation.setIdentifierType("EMAIL");
        invitation.setIdentifierValue("a@b.cv");
        invitation.setStatus(InvitationStatus.CANCELED);

        when(invitationRepository.findByIdOrThrow(7)).thenReturn(invitation);
        when(invitationRepository.save(invitation)).thenReturn(invitation);
        when(userUtils.constructInvitationUrl(any(), anyString())).thenReturn("http://invite");
        when(invitationMapper.toDtoWithUrl(eq(invitation), eq("http://invite"))).thenReturn(new InvitationDTO());
    }

    @Test
    void resendRotatesTokenReopensAndUsesTheInvitationLocale() {
        invitation.setLocale("fr");

        ResponseEntity<InvitationDTO> response = handler.handle(new ResendUserInvitationCommand(7));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(InvitationStatus.PENDING, invitation.getStatus());
        assertNotEquals("old-token", invitation.getToken());
        verify(invitationSender).sendResend(eq("a@b.cv"), eq("a@b.cv"), eq("http://invite"), eq(invitation.getToken()), eq("fr"));
    }

    /** BE-14 (FR-33): pre-i18n invitations pass null so the sender applies the configured default. */
    @Test
    void resendOfPreI18nInvitationPassesNullLocale() {
        handler.handle(new ResendUserInvitationCommand(7));

        verify(invitationSender).sendResend(any(), any(), any(), any(), isNull());
    }
}
