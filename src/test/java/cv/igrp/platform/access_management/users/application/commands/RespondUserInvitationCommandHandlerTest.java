package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.notifications.core.adapter.NotificationAdapter;
import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditService;
import cv.igrp.platform.access_management.shared.application.constants.InvitationStatus;
import cv.igrp.platform.access_management.shared.application.dto.InvitationDTO;
import cv.igrp.platform.access_management.shared.application.dto.UserInvitationResponseDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.InvitationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.UserIdentifierEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.InvitationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.UserIdentifierEntityRepository;
import cv.igrp.platform.access_management.shared.security.IgrpOidcUser;
import cv.igrp.platform.access_management.shared.security.UserProfile;
import cv.igrp.platform.access_management.users.mapper.InvitationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RespondUserInvitationCommandHandler.class)
class RespondUserInvitationCommandHandlerTest {

    @MockBean
    private NotificationAdapter<cv.igrp.framework.notifications.core.model.NotificationResult> notificationAdapter;
    @MockBean
    private IGRPUserEntityRepository userRepository;
    @MockBean
    private RoleEntityRepository roleRepository;
    @MockBean
    private InvitationEntityRepository invitationRepository;
    @MockBean
    private InvitationMapper invitationMapper;
    @MockBean
    private SecurityAuditService auditService;
    @MockBean
    private UserIdentifierEntityRepository userIdentifierEntityRepository;

    @MockBean
    private cv.igrp.platform.access_management.users.application.service.UserIdentityResolutionService userIdentityResolutionService;

    @MockBean
    private cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.OtpEntityRepository otpEntityRepository;

    @Mock
    private SecurityContext securityContext;
    @Mock
    private Authentication authentication;

    @Autowired
    private RespondUserInvitationCommandHandler commandHandler;

    private RespondUserInvitationCommand command;
    private InvitationEntity invitationEntity;
    private UserInvitationResponseDTO responseDto;

    @BeforeEach
    void setUp() {
        responseDto = mock(UserInvitationResponseDTO.class);
        command = new RespondUserInvitationCommand();
        command.setUserinvitationresponsedto(responseDto);
        command.setToken("mock-token");

        invitationEntity = new InvitationEntity();
        invitationEntity.setId(1);
        invitationEntity.setIdentifierValue("jane@example.com");
        invitationEntity.setIdentifierValue("jane@example.com");
        
        RoleEntity role = new RoleEntity();
        role.setId(2);
        role.setUsers(new HashSet<>());
        Set<RoleEntity> roles = new HashSet<>();
        roles.add(role);
        
        invitationEntity.setRoles(roles);

        SecurityContextHolder.setContext(securityContext);
    }

    private void mockSecurityContext(UserProfile profile) {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        IgrpOidcUser oidcUser = mock(IgrpOidcUser.class);
        when(authentication.getPrincipal()).thenReturn(oidcUser);
        when(oidcUser.getUserProfile()).thenReturn(profile);
    }

    @Test
    void testHandle_MissingNativeOidcContext() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn("anonymousUser");
        when(invitationRepository.findByTokenAndStatusPending(any())).thenReturn(invitationEntity);

        IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class, () -> commandHandler.handle(command));
        assertEquals(HttpStatus.UNAUTHORIZED.value(), exception.getBody().getStatus());
        assertTrue(exception.getMessage().contains("Native OIDC User Context required"));
    }

    @Test
    void testHandle_Accept_Without_Valid_Otp_Should_Throw() {
        when(invitationRepository.findByTokenAndStatusPending(any())).thenReturn(invitationEntity);
        UserProfile profile = new UserProfile("sub", null, "John", "other@example.com", null, null, "pwd", List.of());
        mockSecurityContext(profile);
        when(responseDto.isAccept()).thenReturn(true);
        when(otpEntityRepository.findFirstByReferenceIdAndStatusOrderByCreatedAtDesc(any(), any())).thenReturn(Optional.empty());

        IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class, () -> commandHandler.handle(command));
        assertEquals(HttpStatus.BAD_REQUEST.value(), exception.getBody().getStatus());
        assertTrue(exception.getMessage().contains("O código OTP não foi validado."));
    }
@Test
    void testHandle_RejectInvitation() {
        when(invitationRepository.findByTokenAndStatusPending(any())).thenReturn(invitationEntity);
        UserProfile profile = new UserProfile("sub", null, "John", "jane@example.com", null, null, "pwd", List.of());
        mockSecurityContext(profile);
        when(responseDto.isAccept()).thenReturn(false);
        when(responseDto.getObservation()).thenReturn("Not interested");

        ResponseEntity<InvitationDTO> response = commandHandler.handle(command);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(InvitationStatus.REJECTED, invitationEntity.getStatus());
        assertEquals("Not interested", invitationEntity.getComments());
        verify(invitationRepository).save(invitationEntity);
        verify(userRepository, never()).save(any());
    }

    @Test
    void testHandle_AcceptNewUser_Pwd() {
        when(invitationRepository.findByTokenAndStatusPending(any())).thenReturn(invitationEntity);
        UserProfile profile = new UserProfile("sub", null, "Jane", "jane@example.com", null, null, "pwd", List.of());
        mockSecurityContext(profile);
        when(responseDto.isAccept()).thenReturn(true);
        
        cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.OtpEntity otpEntity = new cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.OtpEntity();
        otpEntity.setId(99L);
        when(otpEntityRepository.findFirstByReferenceIdAndStatusOrderByCreatedAtDesc(any(), any())).thenReturn(Optional.of(otpEntity));
        IGRPUserEntity savedUserMock = new IGRPUserEntity();
        savedUserMock.setId(10);
        savedUserMock.setEmail("jane@example.com");
        savedUserMock.setName("Jane");
        when(userIdentityResolutionService.resolveOrCreate(any(), any(), any(), any(), any())).thenReturn(savedUserMock);
        when(userRepository.save(any(IGRPUserEntity.class))).thenReturn(savedUserMock);
        
        RoleEntity roleEntityMock = new RoleEntity();
        roleEntityMock.setId(2);
        when(roleRepository.findById(2)).thenReturn(Optional.of(roleEntityMock));
        
        when(invitationMapper.toDto(any())).thenReturn(new InvitationDTO());

        ResponseEntity<InvitationDTO> response = commandHandler.handle(command);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(InvitationStatus.ACCEPTED, invitationEntity.getStatus());
        
        ArgumentCaptor<IGRPUserEntity> userCaptor = ArgumentCaptor.forClass(IGRPUserEntity.class);
        verify(userRepository).save(userCaptor.capture());
        IGRPUserEntity savedUser = userCaptor.getValue();
        assertEquals("jane@example.com", savedUser.getEmail());
        assertEquals("Jane", savedUser.getName());
        
        verify(auditService).logUserChange(any(), eq("CREATE"));
        
        // Assert user identifier is saved for email
        ArgumentCaptor<UserIdentifierEntity> identifierCaptor = ArgumentCaptor.forClass(UserIdentifierEntity.class);
        verify(userIdentifierEntityRepository).save(identifierCaptor.capture());
        UserIdentifierEntity identifier = identifierCaptor.getValue();
        assertEquals("EMAIL", identifier.getType());
        assertEquals("jane@example.com", identifier.getValueNormalized());
    }

    @Test
    void testHandle_AcceptExistingUser_Cni_WithOtp() {
        invitationEntity.setIdentifierValue("jane@example.com");
        invitationEntity.setIdentifierValue("jane@example.com");
        invitationEntity.setToken("mock-token");
        
        when(invitationRepository.findByTokenAndStatusPending(any())).thenReturn(invitationEntity);
        UserProfile profile = new UserProfile("sub", null, "Jane", null, null, "123456", "cni", List.of());
        mockSecurityContext(profile);
        when(responseDto.isAccept()).thenReturn(true);
        command = new RespondUserInvitationCommand();
        command.setUserinvitationresponsedto(responseDto);
        command.setToken("mock-token");
        
        cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.OtpEntity otpEntity = new cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.OtpEntity();
        otpEntity.setId(99L);
        otpEntity.setStatus("APPROVED");
        otpEntity.setReferenceId("mock-token");
        when(otpEntityRepository.findFirstByReferenceIdAndStatusOrderByCreatedAtDesc("mock-token", "APPROVED")).thenReturn(Optional.of(otpEntity));
        
        IGRPUserEntity existingUser = new IGRPUserEntity();
        existingUser.setId(10);
        existingUser.setNic("123456");
        existingUser.setExternalId("sub");
        when(userRepository.findByAnyIdentifier(any(), any(), any(), any())).thenReturn(Optional.of(existingUser));
        when(userIdentityResolutionService.resolveOrCreate(any(), any(), any(), any(), any())).thenReturn(existingUser);
        when(userRepository.save(any(IGRPUserEntity.class))).thenReturn(existingUser);
        
        RoleEntity roleEntityMock = new RoleEntity();
        roleEntityMock.setId(2);
        when(roleRepository.findById(2)).thenReturn(Optional.of(roleEntityMock));
        
        when(invitationMapper.toDto(any())).thenReturn(new InvitationDTO());

        ResponseEntity<InvitationDTO> response = commandHandler.handle(command);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userRepository).save(existingUser);
        verify(auditService).logUserChange(any(), eq("UPDATE"));
        verify(userIdentifierEntityRepository, never()).save(any());
        assertEquals(99L, invitationEntity.getOtpId());
    }

    @Test
    void testHandle_Accept_Without_Valid_Otp_Should_Throw_For_All() {
        invitationEntity.setIdentifierValue("jane@example.com");
        invitationEntity.setIdentifierValue("jane@example.com");
        when(invitationRepository.findByTokenAndStatusPending(any())).thenReturn(invitationEntity);
        UserProfile profile = new UserProfile("sub", null, "John", null, null, "123456", "cni", List.of());
        mockSecurityContext(profile);
        command = new RespondUserInvitationCommand();
        command.setUserinvitationresponsedto(responseDto);
        command.setToken("mock-token");
        when(responseDto.isAccept()).thenReturn(true);
        when(otpEntityRepository.findFirstByReferenceIdAndStatusOrderByCreatedAtDesc(any(), any())).thenReturn(Optional.empty());

        IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class, () -> commandHandler.handle(command));
        assertEquals(HttpStatus.BAD_REQUEST.value(), exception.getBody().getStatus());
        assertTrue(exception.getMessage().contains("O código OTP não foi validado."));
    }

    @Test
    void testHandle_AcceptNewUser_Cmdcv_WithOtp() {
        invitationEntity.setIdentifierValue("jane@example.com");
        invitationEntity.setIdentifierValue("jane@example.com");
        invitationEntity.setToken("mock-token");
        
        when(invitationRepository.findByTokenAndStatusPending(any())).thenReturn(invitationEntity);
        UserProfile profile = new UserProfile("sub", null, "Jane", null, "+2389999999", null, "cmdcv", List.of());
        mockSecurityContext(profile);
        when(responseDto.isAccept()).thenReturn(true);
        command = new RespondUserInvitationCommand();
        command.setUserinvitationresponsedto(responseDto);
        command.setToken("mock-token");

        cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.OtpEntity otpEntity = new cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.OtpEntity();
        otpEntity.setId(99L);
        otpEntity.setStatus("APPROVED");
        otpEntity.setReferenceId("mock-token");
        when(otpEntityRepository.findFirstByReferenceIdAndStatusOrderByCreatedAtDesc("mock-token", "APPROVED")).thenReturn(Optional.of(otpEntity));
                
        IGRPUserEntity savedUserMock = new IGRPUserEntity();
        savedUserMock.setId(10);
        when(userIdentityResolutionService.resolveOrCreate(any(), any(), any(), any(), any())).thenReturn(savedUserMock);
        when(userRepository.save(any(IGRPUserEntity.class))).thenReturn(savedUserMock);
        
        RoleEntity roleEntityMock = new RoleEntity();
        roleEntityMock.setId(2);
        when(roleRepository.findById(2)).thenReturn(Optional.of(roleEntityMock));
        
        when(invitationMapper.toDto(any())).thenReturn(new InvitationDTO());

        ResponseEntity<InvitationDTO> response = commandHandler.handle(command);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        ArgumentCaptor<UserIdentifierEntity> identifierCaptor = ArgumentCaptor.forClass(UserIdentifierEntity.class);
        verify(userIdentifierEntityRepository).save(identifierCaptor.capture());
        UserIdentifierEntity identifier = identifierCaptor.getValue();
        assertEquals("PHONE", identifier.getType());
        assertEquals("+2389999999", identifier.getValueNormalized());
        assertEquals(99L, invitationEntity.getOtpId());
    }
}
