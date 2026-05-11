package cv.igrp.platform.access_management.users.application.commands;
import static org.mockito.ArgumentMatchers.anyInt;

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
    private cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.UserIdentifierEntityRepository userIdentifierEntityRepository;

    @Mock
    private cv.igrp.platform.access_management.users.application.service.UserIdentityResolutionService userIdentityResolutionService;

    @Mock
    private cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.OtpEntityRepository otpEntityRepository;

    @Mock
    private cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.UserRoleAssignmentRepository userRoleAssignmentRepository;

    @Mock
    private cv.igrp.platform.access_management.users.infrastructure.service.ExpireRoleService expireRoleService;

    @Mock
    private cv.igrp.platform.access_management.shared.domain.events.EventPublisher eventPublisher;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @Mock
    private cv.igrp.platform.access_management.shared.security.IgrpOidcUser oidcUser;

    @InjectMocks
    private RespondUserInvitationCommandHandler handler;

    private String token = "123";

    @BeforeEach
    void setUp() {
        // Mock SecurityContext
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(oidcUser);
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
        invitation.setStatus(InvitationStatus.PENDING);
        RoleEntity role = new RoleEntity();
        role.setId(1);
        invitation.setRoles(Set.of(role));

        when(invitationRepository.findByTokenAndStatusPending(token)).thenReturn(invitation);
        cv.igrp.platform.access_management.shared.security.UserProfile profile = new cv.igrp.platform.access_management.shared.security.UserProfile(
                "123", "issuer", "Test User", "test@example.com", null, null, "pwd", java.util.List.of()
        );
        when(oidcUser.getUserProfile()).thenReturn(profile);
        when(invitationRepository.save(any())).thenReturn(invitation);
        when(userRepository.findById(anyInt())).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleRepository.findById(1)).thenReturn(Optional.of(role));
        when(invitationMapper.toDto(any())).thenReturn(new InvitationDTO());

        // Act
        ResponseEntity<InvitationDTO> response = handler.handle(command);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(invitationRepository).save(invitation);
        verify(userRepository).save(any(IGRPUserEntity.class));
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
        invitation.setStatus(InvitationStatus.PENDING);

        when(invitationRepository.findByTokenAndStatusPending(token)).thenReturn(invitation);
        cv.igrp.platform.access_management.shared.security.UserProfile profile = new cv.igrp.platform.access_management.shared.security.UserProfile(
                "123", "issuer", "Test User", "test@example.com", null, null, "pwd", java.util.List.of()
        );
        when(oidcUser.getUserProfile()).thenReturn(profile);

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
        invitation.setStatus(InvitationStatus.PENDING);

        when(invitationRepository.findByTokenAndStatusPending(token)).thenReturn(invitation);
        cv.igrp.platform.access_management.shared.security.UserProfile profile = new cv.igrp.platform.access_management.shared.security.UserProfile(
                "123", "issuer", "Test User", "mismatch@example.com", null, null, "pwd", java.util.List.of()
        );
        when(oidcUser.getUserProfile()).thenReturn(profile);

        // Act & Assert
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> handler.handle(command));

        assertEquals(HttpStatus.BAD_REQUEST.value(), ex.getBody().getStatus());
        assertTrue(ex.getMessage().contains("Authenticated identifier does not match"));
    }

    @Test
    void handle_validIntegerUserIdFromProfile_success() {
        // Arrange - Tests the refactored Integer ID lookup (feature/config-user-by-id)
        UserInvitationResponseDTO dto = new UserInvitationResponseDTO();
        dto.setAccept(true);

        RespondUserInvitationCommand command = new RespondUserInvitationCommand(dto, token);

        InvitationEntity invitation = new InvitationEntity();
        invitation.setIdentifierType("EMAIL");
        invitation.setIdentifierValue("user@example.com");
        invitation.setStatus(InvitationStatus.PENDING);
        RoleEntity role = new RoleEntity();
        role.setId(1);
        invitation.setRoles(Set.of(role));

        when(invitationRepository.findByTokenAndStatusPending(token)).thenReturn(invitation);
        cv.igrp.platform.access_management.shared.security.UserProfile profile = new cv.igrp.platform.access_management.shared.security.UserProfile(
                "456", "issuer", "Test User", "user@example.com", null, null, "pwd", java.util.List.of()
        );
        when(oidcUser.getUserProfile()).thenReturn(profile);

        when(invitationRepository.save(any())).thenReturn(invitation);
        when(userRepository.findById(456)).thenReturn(Optional.empty()); // Integer lookup instead of String
        IGRPUserEntity savedUser = new IGRPUserEntity();
        savedUser.setId(456);
        when(userRepository.save(any())).thenReturn(savedUser);
        when(roleRepository.findById(1)).thenReturn(Optional.of(role));
        when(invitationMapper.toDto(any())).thenReturn(new InvitationDTO());

        // Act
        ResponseEntity<InvitationDTO> response = handler.handle(command);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        // Verify that findById was called with Integer 456, not String "456"
        verify(userRepository).findById(456);
        verify(auditService).logUserChange("456", "CREATE");
    }

    @Test
    void handle_invalidIntegerUserIdFromProfile_throwsException() {
        // Arrange - Tests validation of Integer conversion (feature/config-user-by-id)
        UserInvitationResponseDTO dto = new UserInvitationResponseDTO();
        dto.setAccept(true);

        RespondUserInvitationCommand command = new RespondUserInvitationCommand(dto, token);

        InvitationEntity invitation = new InvitationEntity();
        invitation.setIdentifierType("EMAIL");
        invitation.setIdentifierValue("user@example.com");
        invitation.setStatus(InvitationStatus.PENDING);

        when(invitationRepository.findByTokenAndStatusPending(token)).thenReturn(invitation);
        cv.igrp.platform.access_management.shared.security.UserProfile profile = new cv.igrp.platform.access_management.shared.security.UserProfile(
                "not-a-number", "issuer", "Test User", "user@example.com", null, null, "pwd", java.util.List.of()
        );
        when(oidcUser.getUserProfile()).thenReturn(profile);

        // Act & Assert
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> handler.handle(command));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("Invalid Token sub: must be an integer ID"));
    }

    @Test
    void handle_userFoundByIntegerId_updatesExistingUser() {
        // Arrange - Tests Integer ID lookup for existing user (feature/config-user-by-id)
        UserInvitationResponseDTO dto = new UserInvitationResponseDTO();
        dto.setAccept(true);

        RespondUserInvitationCommand command = new RespondUserInvitationCommand(dto, token);

        InvitationEntity invitation = new InvitationEntity();
        invitation.setIdentifierType("EMAIL");
        invitation.setIdentifierValue("existing@example.com");
        invitation.setStatus(InvitationStatus.PENDING);
        RoleEntity role = new RoleEntity();
        role.setId(1);
        invitation.setRoles(Set.of(role));

        when(invitationRepository.findByTokenAndStatusPending(token)).thenReturn(invitation);
        cv.igrp.platform.access_management.shared.security.UserProfile profile = new cv.igrp.platform.access_management.shared.security.UserProfile(
                "789", "issuer", "Existing User", "existing@example.com", null, null, "pwd", java.util.List.of()
        );
        when(oidcUser.getUserProfile()).thenReturn(profile);

        IGRPUserEntity existingUser = new IGRPUserEntity();
        existingUser.setId(789);
        existingUser.setEmail("existing@example.com");

        when(invitationRepository.save(any())).thenReturn(invitation);
        when(userRepository.findById(789)).thenReturn(Optional.of(existingUser)); // User exists
        when(userRepository.save(any())).thenReturn(existingUser);
        when(roleRepository.findById(1)).thenReturn(Optional.of(role));
        when(invitationMapper.toDto(any())).thenReturn(new InvitationDTO());

        // Act
        ResponseEntity<InvitationDTO> response = handler.handle(command);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userRepository).findById(789);
        verify(auditService).logUserChange("789", "UPDATE"); // UPDATE instead of CREATE
    }
}

