package cv.igrp.platform.access_management.users.application.commands;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.framework.notifications.core.adapter.NotificationAdapter;
import cv.igrp.framework.notifications.core.exception.NotificationException;
import cv.igrp.framework.notifications.core.model.NotificationResult;
import cv.igrp.platform.access_management.shared.application.constants.InvitationStatus;
import cv.igrp.platform.access_management.shared.application.dto.InvitationDTO;
import cv.igrp.platform.access_management.shared.application.dto.UserInvitationResponseDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.InvitationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.InvitationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import cv.igrp.platform.access_management.users.mapper.InvitationMapper;
import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
class RespondUserInvitationCommandHandlerTest {

    @Mock
    private NotificationAdapter<NotificationResult> notificationAdapter;

    @Mock
    private IGRPUserEntityRepository userRepository;

    @Mock
    private RoleEntityRepository roleRepository;

    @Mock
    private InvitationEntityRepository invitationRepository;

    @Mock
    private InvitationMapper invitationMapper;

    @Mock
    private SecurityAuditService auditService;

    @Mock
    private cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.UserRoleAssignmentRepository userRoleAssignmentRepository;

    @Mock
    private cv.igrp.platform.access_management.users.infrastructure.service.ExpireRoleService expireRoleService;

    @Mock
    private org.springframework.security.core.context.SecurityContext securityContext;

    @Mock
    private org.springframework.security.core.Authentication authentication;

    @Mock
    private cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.UserIdentifierEntityRepository userIdentifierEntityRepository;

    @InjectMocks
    private RespondUserInvitationCommandHandler handler;

    private String token = "valid-token";

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);
    }

    private void mockSecurityContext(cv.igrp.platform.access_management.shared.security.UserProfile profile) {
        cv.igrp.platform.access_management.shared.security.IgrpOidcUser oidcUser = 
            mock(cv.igrp.platform.access_management.shared.security.IgrpOidcUser.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(oidcUser);
        when(oidcUser.getUserProfile()).thenReturn(profile);
    }

    @Test
    void handle_acceptInvitation_success() throws NotificationException {
        // Arrange
        UserInvitationResponseDTO dto = new UserInvitationResponseDTO();
        dto.setAccept(true);

        RespondUserInvitationCommand command = new RespondUserInvitationCommand(dto, token);

        InvitationEntity invitation = new InvitationEntity();
        invitation.setIdentifierType("EMAIL");
        invitation.setIdentifierValue("test@example.com");
        invitation.setAllowedAuthMethods(Set.of("pwd"));
        invitation.setStatus(InvitationStatus.PENDING);
        RoleEntity role = new RoleEntity();
        role.setId(1);
        role.setCode("ADMIN");
        invitation.setRoles(Set.of(role));

        cv.igrp.platform.access_management.shared.security.UserProfile profile = 
            new cv.igrp.platform.access_management.shared.security.UserProfile(
                "ext-123", "iss", "Test User", "test@example.com", null, "ext-123", "pwd", java.util.List.of()
            );
        mockSecurityContext(profile);

        when(invitationRepository.findByTokenAndStatusPending(token)).thenReturn(invitation);
        when(invitationRepository.save(any())).thenReturn(invitation);
        when(userRepository.findByExternalId(any())).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(roleRepository.findById(1)).thenReturn(Optional.of(role));
        when(invitationMapper.toDto(any())).thenReturn(new InvitationDTO());

        // Act
        ResponseEntity<InvitationDTO> response = handler.handle(command);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(invitationRepository).save(invitation);
        verify(userRepository).save(any(IGRPUserEntity.class));
        verify(userRoleAssignmentRepository).save(any());
        verify(auditService).logUserChange(any(), eq("CREATE"));
    }

    @Test
    void handle_rejectInvitation_success() {
        // Arrange
        UserInvitationResponseDTO dto = new UserInvitationResponseDTO();
        dto.setAccept(false);
        dto.setObservation("Rejected");

        RespondUserInvitationCommand command = new RespondUserInvitationCommand(dto, token);

        InvitationEntity invitation = new InvitationEntity();
        invitation.setIdentifierType("EMAIL");
        invitation.setIdentifierValue("test@example.com");
        invitation.setAllowedAuthMethods(Set.of("pwd"));
        invitation.setStatus(InvitationStatus.PENDING);

        cv.igrp.platform.access_management.shared.security.UserProfile profile = 
            new cv.igrp.platform.access_management.shared.security.UserProfile(
                "ext-123", "iss", "Test User", "test@example.com", null, "ext-123", "pwd", java.util.List.of()
            );
        mockSecurityContext(profile);

        when(invitationRepository.findByTokenAndStatusPending(token)).thenReturn(invitation);

        // Act
        ResponseEntity<InvitationDTO> response = handler.handle(command);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(InvitationStatus.REJECTED, invitation.getStatus());
        assertEquals("Rejected", invitation.getComments());
        verify(invitationRepository).save(invitation);
        verifyNoInteractions(userRepository);
        verifyNoInteractions(notificationAdapter);
    }

    @Test
    void handle_emailMismatch_throwsException() {
        // Arrange
        UserInvitationResponseDTO dto = new UserInvitationResponseDTO();
        dto.setAccept(true);

        RespondUserInvitationCommand command = new RespondUserInvitationCommand(dto, token);

        InvitationEntity invitation = new InvitationEntity();
        invitation.setIdentifierType("EMAIL");
        invitation.setIdentifierValue("test@example.com");
        invitation.setAllowedAuthMethods(Set.of("pwd"));
        invitation.setStatus(InvitationStatus.PENDING);

        cv.igrp.platform.access_management.shared.security.UserProfile profile = 
            new cv.igrp.platform.access_management.shared.security.UserProfile(
                "ext-123", "iss", "Test User", "mismatch@example.com", null, "ext-123", "pwd", java.util.List.of()
            );
        mockSecurityContext(profile);

        when(invitationRepository.findByTokenAndStatusPending(token)).thenReturn(invitation);

        // Act & Assert
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> handler.handle(command));

        assertEquals(HttpStatus.BAD_REQUEST.value(), ex.getBody().getStatus());
        assertTrue(ex.getMessage().contains("Authenticated identifier does not match"));
    }
}

