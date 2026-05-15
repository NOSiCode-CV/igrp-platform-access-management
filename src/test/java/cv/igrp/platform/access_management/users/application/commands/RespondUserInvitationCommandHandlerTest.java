package cv.igrp.platform.access_management.users.application.commands;
import static org.mockito.ArgumentMatchers.anyString;

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
    private cv.igrp.platform.access_management.session.infrastructure.audit.SessionAuditLogger sessionAuditLogger;

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
        // Mock SecurityContext — lenient because some tests (e.g. the 409 fast-fail
        // paths that throw before the auth lookup is reached) legitimately don't
        // consume these stubs.
        SecurityContextHolder.setContext(securityContext);
        org.mockito.Mockito.lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        org.mockito.Mockito.lenient().when(authentication.getPrincipal()).thenReturn(oidcUser);
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

        when(invitationRepository.findByTokenOrThrow(token)).thenReturn(invitation);
        cv.igrp.platform.access_management.shared.security.UserProfile profile = new cv.igrp.platform.access_management.shared.security.UserProfile(
                "00000000-0000-0000-0000-000000000123", "issuer", "Test User", "test@example.com", null, null, "pwd", java.util.List.of()
        );
        when(oidcUser.getUserProfile()).thenReturn(profile);
        when(invitationRepository.save(any())).thenReturn(invitation);
        when(userRepository.findById(anyString())).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleRepository.findById(1)).thenReturn(Optional.of(role));
        when(invitationMapper.toDto(any())).thenReturn(new InvitationDTO());

        // Phase G3: accept now requires an APPROVED OTP record for the token.
        var otp = new cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.OtpEntity();
        otp.setId(1L);
        when(otpEntityRepository.findFirstByReferenceIdAndStatusOrderByCreatedAtDesc(token, "APPROVED"))
                .thenReturn(Optional.of(otp));

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

        when(invitationRepository.findByTokenOrThrow(token)).thenReturn(invitation);
        cv.igrp.platform.access_management.shared.security.UserProfile profile = new cv.igrp.platform.access_management.shared.security.UserProfile(
                "00000000-0000-0000-0000-000000000123", "issuer", "Test User", "test@example.com", null, null, "pwd", java.util.List.of()
        );
        when(oidcUser.getUserProfile()).thenReturn(profile);
        // Phase G3: reject path now looks up the authenticated user to flip
        // TEMPORARY → DELETED. Returning empty exercises the no-op branch so
        // the rest of the assertions remain valid.
        when(userRepository.findById("00000000-0000-0000-0000-000000000123")).thenReturn(Optional.empty());

        // Act
        ResponseEntity<InvitationDTO> response = handler.handle(command);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(InvitationStatus.REJECTED, invitation.getStatus());
        assertEquals("Rejected", invitation.getComments());
        verify(invitationRepository).save(invitation);
        verifyNoInteractions(notificationAdapter);
    }

    @Test
    void handle_acceptWithoutApprovedOtp_throwsBadRequest() {
        // Phase G3: accepting an invitation now requires an APPROVED OTP record
        // for the token. Without one, the request must be rejected with 400.
        // (Previously a separate email-vs-token mismatch check guarded this; the
        // OTP gate has subsumed that role.)
        UserInvitationResponseDTO dto = new UserInvitationResponseDTO();
        dto.setAccept(true);

        RespondUserInvitationCommand command = new RespondUserInvitationCommand(dto, token);

        InvitationEntity invitation = new InvitationEntity();
        invitation.setIdentifierType("EMAIL");
        invitation.setIdentifierValue("test@example.com");
        invitation.setStatus(InvitationStatus.PENDING);

        when(invitationRepository.findByTokenOrThrow(token)).thenReturn(invitation);
        cv.igrp.platform.access_management.shared.security.UserProfile profile = new cv.igrp.platform.access_management.shared.security.UserProfile(
                "00000000-0000-0000-0000-000000000123", "issuer", "Test User", "mismatch@example.com", null, null, "pwd", java.util.List.of()
        );
        when(oidcUser.getUserProfile()).thenReturn(profile);
        // No APPROVED OTP for this token
        when(otpEntityRepository.findFirstByReferenceIdAndStatusOrderByCreatedAtDesc(token, "APPROVED"))
                .thenReturn(Optional.empty());

        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> handler.handle(command));

        assertEquals(HttpStatus.BAD_REQUEST.value(), ex.getBody().getStatus());
        assertTrue(ex.getBody().getTitle() != null
                && ex.getBody().getTitle().toLowerCase().contains("otp"));
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

        when(invitationRepository.findByTokenOrThrow(token)).thenReturn(invitation);
        cv.igrp.platform.access_management.shared.security.UserProfile profile = new cv.igrp.platform.access_management.shared.security.UserProfile(
                "00000000-0000-0000-0000-000000000456", "issuer", "Test User", "user@example.com", null, null, "pwd", java.util.List.of()
        );
        when(oidcUser.getUserProfile()).thenReturn(profile);

        when(invitationRepository.save(any())).thenReturn(invitation);
        when(userRepository.findById("00000000-0000-0000-0000-000000000456")).thenReturn(Optional.empty());
        IGRPUserEntity savedUser = new IGRPUserEntity();
        savedUser.setId("00000000-0000-0000-0000-000000000456");
        when(userRepository.save(any())).thenReturn(savedUser);
        when(roleRepository.findById(1)).thenReturn(Optional.of(role));
        when(invitationMapper.toDto(any())).thenReturn(new InvitationDTO());

        // Phase G3: accept requires an APPROVED OTP record.
        var otp = new cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.OtpEntity();
        otp.setId(2L);
        when(otpEntityRepository.findFirstByReferenceIdAndStatusOrderByCreatedAtDesc(token, "APPROVED"))
                .thenReturn(Optional.of(otp));

        // Act
        ResponseEntity<InvitationDTO> response = handler.handle(command);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        // Verify that findById was called with String UUID
        verify(userRepository).findById("00000000-0000-0000-0000-000000000456");
        verify(auditService).logUserChange("00000000-0000-0000-0000-000000000456", "CREATE");
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

        when(invitationRepository.findByTokenOrThrow(token)).thenReturn(invitation);
        cv.igrp.platform.access_management.shared.security.UserProfile profile = new cv.igrp.platform.access_management.shared.security.UserProfile(
                "not-a-number", "issuer", "Test User", "user@example.com", null, null, "pwd", java.util.List.of()
        );
        when(oidcUser.getUserProfile()).thenReturn(profile);

        // Act & Assert — Phase G1 / FR-13: non-numeric JWT sub now raises a
        // typed InvalidPrincipalException (AuthenticationException) which the
        // global exception handler maps to HTTP 401. Replaces the previous
        // IgrpResponseStatusException(401, "Invalid Token sub: must be an integer ID").
        cv.igrp.platform.access_management.shared.security.InvalidPrincipalException ex =
                assertThrows(
                        cv.igrp.platform.access_management.shared.security.InvalidPrincipalException.class,
                        () -> handler.handle(command));
        assertEquals("non_uuid_sub", ex.getMessage());
    }

    @Test
    void handle_acceptInvitation_promotesTemporaryUserToActive() {
        // Phase G3: existing TEMPORARY user accepting invitation must be promoted
        // to ACTIVE and publish a UserStatusChangedEvent.
        UserInvitationResponseDTO dto = new UserInvitationResponseDTO();
        dto.setAccept(true);

        RespondUserInvitationCommand command = new RespondUserInvitationCommand(dto, token);

        InvitationEntity invitation = new InvitationEntity();
        invitation.setIdentifierType("EMAIL");
        invitation.setIdentifierValue("temp@example.com");
        invitation.setStatus(InvitationStatus.PENDING);
        RoleEntity role = new RoleEntity();
        role.setId(1);
        invitation.setRoles(Set.of(role));

        when(invitationRepository.findByTokenOrThrow(token)).thenReturn(invitation);
        cv.igrp.platform.access_management.shared.security.UserProfile profile =
                new cv.igrp.platform.access_management.shared.security.UserProfile(
                        "00000000-0000-0000-0000-000000000111", "issuer", "Temp User", "temp@example.com",
                        null, null, "pwd", java.util.List.of());
        when(oidcUser.getUserProfile()).thenReturn(profile);

        IGRPUserEntity tempUser = new IGRPUserEntity();
        tempUser.setId("00000000-0000-0000-0000-000000000111");
        tempUser.setEmail("temp@example.com");
        tempUser.setStatus(cv.igrp.platform.access_management.shared.application.constants.Status.TEMPORARY);

        when(invitationRepository.save(any())).thenReturn(invitation);
        when(userRepository.findById("00000000-0000-0000-0000-000000000111")).thenReturn(Optional.of(tempUser));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(roleRepository.findById(1)).thenReturn(Optional.of(role));
        when(invitationMapper.toDto(any())).thenReturn(new InvitationDTO());

        var otp = new cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.OtpEntity();
        otp.setId(1L);
        when(otpEntityRepository.findFirstByReferenceIdAndStatusOrderByCreatedAtDesc(token, "APPROVED"))
                .thenReturn(Optional.of(otp));

        ResponseEntity<InvitationDTO> response = handler.handle(command);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(cv.igrp.platform.access_management.shared.application.constants.Status.ACTIVE, tempUser.getStatus());
        verify(eventPublisher).publishUserStatusChanged(argThat(ev ->
                "TEMPORARY".equals(ev.getPreviousStatus())
                        && "ACTIVE".equals(ev.getNewStatus())
                        && "INVITATION_ACCEPTED".equals(ev.getTriggeredBy())));
        verify(sessionAuditLogger).recordUserStatusTransitioned(eq("00000000-0000-0000-0000-000000000111"),
                eq("TEMPORARY"), eq("ACTIVE"), anyString(), eq("INVITATION_ACCEPTED"));
    }

    @Test
    void handle_rejectInvitation_flipsTemporaryUserToDeleted() {
        // Phase G3: rejecting an invitation must terminally flip the user
        // (TEMPORARY → DELETED) and publish a UserStatusChangedEvent.
        UserInvitationResponseDTO dto = new UserInvitationResponseDTO();
        dto.setAccept(false);
        dto.setObservation("nope");

        RespondUserInvitationCommand command = new RespondUserInvitationCommand(dto, token);

        InvitationEntity invitation = new InvitationEntity();
        invitation.setIdentifierType("EMAIL");
        invitation.setIdentifierValue("temp2@example.com");
        invitation.setStatus(InvitationStatus.PENDING);

        when(invitationRepository.findByTokenOrThrow(token)).thenReturn(invitation);
        cv.igrp.platform.access_management.shared.security.UserProfile profile =
                new cv.igrp.platform.access_management.shared.security.UserProfile(
                        "00000000-0000-0000-0000-000000000222", "issuer", "Temp Two", "temp2@example.com",
                        null, null, "pwd", java.util.List.of());
        when(oidcUser.getUserProfile()).thenReturn(profile);

        IGRPUserEntity tempUser = new IGRPUserEntity();
        tempUser.setId("00000000-0000-0000-0000-000000000222");
        tempUser.setStatus(cv.igrp.platform.access_management.shared.application.constants.Status.TEMPORARY);
        when(userRepository.findById("00000000-0000-0000-0000-000000000222")).thenReturn(Optional.of(tempUser));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<InvitationDTO> response = handler.handle(command);

        assertNotNull(response);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(InvitationStatus.REJECTED, invitation.getStatus());
        assertEquals(cv.igrp.platform.access_management.shared.application.constants.Status.DELETED, tempUser.getStatus());
        verify(eventPublisher).publishUserStatusChanged(argThat(ev ->
                "TEMPORARY".equals(ev.getPreviousStatus())
                        && "DELETED".equals(ev.getNewStatus())
                        && "INVITATION_REJECTED".equals(ev.getTriggeredBy())));
        verify(sessionAuditLogger).recordUserStatusTransitioned(eq("00000000-0000-0000-0000-000000000222"),
                eq("TEMPORARY"), eq("DELETED"), anyString(), eq("INVITATION_REJECTED"));
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

        when(invitationRepository.findByTokenOrThrow(token)).thenReturn(invitation);
        cv.igrp.platform.access_management.shared.security.UserProfile profile = new cv.igrp.platform.access_management.shared.security.UserProfile(
                "00000000-0000-0000-0000-000000000789", "issuer", "Existing User", "existing@example.com", null, null, "pwd", java.util.List.of()
        );
        when(oidcUser.getUserProfile()).thenReturn(profile);

        IGRPUserEntity existingUser = new IGRPUserEntity();
        existingUser.setId("00000000-0000-0000-0000-000000000789");
        existingUser.setEmail("existing@example.com");

        when(invitationRepository.save(any())).thenReturn(invitation);
        when(userRepository.findById("00000000-0000-0000-0000-000000000789")).thenReturn(Optional.of(existingUser)); // User exists
        when(userRepository.save(any())).thenReturn(existingUser);
        when(roleRepository.findById(1)).thenReturn(Optional.of(role));
        when(invitationMapper.toDto(any())).thenReturn(new InvitationDTO());

        // Phase G3: accept requires an APPROVED OTP record.
        var otp = new cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.OtpEntity();
        otp.setId(3L);
        when(otpEntityRepository.findFirstByReferenceIdAndStatusOrderByCreatedAtDesc(token, "APPROVED"))
                .thenReturn(Optional.of(otp));

        // Act
        ResponseEntity<InvitationDTO> response = handler.handle(command);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userRepository).findById("00000000-0000-0000-0000-000000000789");
        verify(auditService).logUserChange("00000000-0000-0000-0000-000000000789", "UPDATE"); // UPDATE instead of CREATE
    }

    /**
     * Regression: a second accept on an already-ACCEPTED invitation must
     * return 409 Conflict, not silently re-run the accept flow (which would
     * double-grant roles and throw NonUniqueObjectException).
     */
    @Test
    void handle_acceptingAlreadyAcceptedInvitation_returns409Conflict() {
        // Arrange
        String token = "already-accepted-token";
        cv.igrp.platform.access_management.shared.application.dto.UserInvitationResponseDTO dto =
                new cv.igrp.platform.access_management.shared.application.dto.UserInvitationResponseDTO();
        dto.setAccept(true);
        RespondUserInvitationCommand command = new RespondUserInvitationCommand(dto, token);

        InvitationEntity invitation = new InvitationEntity();
        invitation.setIdentifierType("EMAIL");
        invitation.setIdentifierValue("test@example.com");
        invitation.setStatus(InvitationStatus.ACCEPTED);
        when(invitationRepository.findByTokenOrThrow(token)).thenReturn(invitation);

        // Act + Assert
        cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException ex =
                org.junit.jupiter.api.Assertions.assertThrows(
                        cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException.class,
                        () -> handler.handle(command));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(invitationRepository, org.mockito.Mockito.never()).save(any());
        verify(userRepository, org.mockito.Mockito.never()).save(any());
    }

    /**
     * Regression: a second accept-attempt on a REJECTED invitation must also
     * return 409, not 404 from the pending-only finder.
     */
    @Test
    void handle_respondingToRejectedInvitation_returns409Conflict() {
        String token = "rejected-token";
        cv.igrp.platform.access_management.shared.application.dto.UserInvitationResponseDTO dto =
                new cv.igrp.platform.access_management.shared.application.dto.UserInvitationResponseDTO();
        dto.setAccept(true);
        RespondUserInvitationCommand command = new RespondUserInvitationCommand(dto, token);

        InvitationEntity invitation = new InvitationEntity();
        invitation.setStatus(InvitationStatus.REJECTED);
        when(invitationRepository.findByTokenOrThrow(token)).thenReturn(invitation);

        cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException ex =
                org.junit.jupiter.api.Assertions.assertThrows(
                        cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException.class,
                        () -> handler.handle(command));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    /**
     * Regression: accept-flow must be idempotent when a UserRoleAssignment
     * with the same composite PK (userId, roleId) already exists — the
     * handler should refresh the existing row (clear expiresAt, bump
     * assignedAt) instead of constructing a new entity that Hibernate would
     * reject with NonUniqueObjectException.
     */
    @Test
    void handle_acceptWhenRoleAssignmentAlreadyExists_updatesExistingNotCreatesNew() {
        // Arrange
        String token = "accept-with-existing-role";
        cv.igrp.platform.access_management.shared.application.dto.UserInvitationResponseDTO dto =
                new cv.igrp.platform.access_management.shared.application.dto.UserInvitationResponseDTO();
        dto.setAccept(true);
        RespondUserInvitationCommand command = new RespondUserInvitationCommand(dto, token);

        String existingUserId = "0ab33988-489d-440a-b99d-5ff0aab21262";
        InvitationEntity invitation = new InvitationEntity();
        invitation.setIdentifierType("EMAIL");
        invitation.setIdentifierValue("test@example.com");
        invitation.setStatus(InvitationStatus.PENDING);
        RoleEntity role = new RoleEntity();
        role.setId(1);
        invitation.setRoles(Set.of(role));
        when(invitationRepository.findByTokenOrThrow(token)).thenReturn(invitation);

        cv.igrp.platform.access_management.shared.security.UserProfile profile =
                new cv.igrp.platform.access_management.shared.security.UserProfile(
                        existingUserId, "issuer", "Test User", "test@example.com",
                        null, null, "pwd", java.util.List.of());
        when(oidcUser.getUserProfile()).thenReturn(profile);

        IGRPUserEntity existingUser = new IGRPUserEntity();
        existingUser.setId(existingUserId);
        existingUser.setStatus(cv.igrp.platform.access_management.shared.application.constants.Status.TEMPORARY);
        when(userRepository.findById(existingUserId)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(roleRepository.findById(1)).thenReturn(Optional.of(role));
        when(invitationRepository.save(any())).thenReturn(invitation);
        when(invitationMapper.toDto(any())).thenReturn(new InvitationDTO());

        var otp = new cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.OtpEntity();
        otp.setId(99L);
        when(otpEntityRepository.findFirstByReferenceIdAndStatusOrderByCreatedAtDesc(token, "APPROVED"))
                .thenReturn(Optional.of(otp));

        // Existing assignment in the DB for the same (userId, roleId).
        cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.UserRoleId existingId =
                new cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.UserRoleId(
                        existingUserId, 1);
        cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.UserRoleAssignment existingUra =
                new cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.UserRoleAssignment(
                        existingUser, role, null);
        when(userRoleAssignmentRepository.findById(existingId)).thenReturn(Optional.of(existingUra));
        when(userRoleAssignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        ResponseEntity<InvitationDTO> response = handler.handle(command);

        // Assert: the existing entity instance is the one saved (not a freshly-constructed one)
        assertEquals(HttpStatus.OK, response.getStatusCode());
        org.mockito.ArgumentCaptor<cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.UserRoleAssignment> captor =
                org.mockito.ArgumentCaptor.forClass(
                        cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.UserRoleAssignment.class);
        verify(userRoleAssignmentRepository).save(captor.capture());
        org.junit.jupiter.api.Assertions.assertSame(existingUra, captor.getValue(),
                "Expected the existing UserRoleAssignment instance to be reused, not a fresh one");
        org.junit.jupiter.api.Assertions.assertNotNull(captor.getValue().getAssignedAt(),
                "assignedAt must be refreshed on idempotent re-assignment");
    }
}

