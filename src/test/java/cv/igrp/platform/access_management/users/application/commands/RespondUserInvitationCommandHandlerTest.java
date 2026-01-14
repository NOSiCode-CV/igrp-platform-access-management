package cv.igrp.platform.access_management.users.application.commands;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.framework.auth.core.adapter.IAdapter;
import cv.igrp.framework.notifications.core.adapter.NotificationAdapter;
import cv.igrp.framework.notifications.core.exception.NotificationException;
import cv.igrp.framework.notifications.core.model.NotificationResult;
import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import cv.igrp.platform.access_management.shared.application.dto.InvitationDTO;
import cv.igrp.platform.access_management.shared.application.dto.UserInvitationResponseDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.users.mapper.IGRPUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class RespondUserInvitationCommandHandlerTest {

    @Mock
    private NotificationAdapter<NotificationResult> notificationAdapter;

    @Mock
    private IGRPUserEntityRepository userRepository;

    @Mock
    private IGRPUserMapper userMapper;

    @Mock
    private IAdapter adapter;

    @Mock
    private UpdateUserStatusCommandHandler commandBus;

    @InjectMocks
    private RespondUserInvitationCommandHandler handler;

    private IGRPUserEntity userEntity;

    String token = "valid-token";

    @BeforeEach
    void setUp() {
        userEntity = new IGRPUserEntity();
        userEntity.setId(1);
        userEntity.setEmail("test@example.com");
    }

    @Test
    void handle_acceptInvitation_success() throws NotificationException {
        // Arrange
        UserInvitationResponseDTO dto = new UserInvitationResponseDTO();
        dto.setEmail("test@example.com");
        dto.setAccept(true);

        RespondUserInvitationCommand command = new RespondUserInvitationCommand(dto, token);

        when(userRepository.existsByEmail(dto.getEmail())).thenReturn(true);
        when(adapter.resolveUser(dto.getEmail())).thenReturn(Optional.of(userEntity));
        when(userRepository.findByExternalId(userEntity.getExternalId())).thenReturn(Optional.of(userEntity));
        when(userRepository.save(any())).thenReturn(userEntity);
        when(userMapper.toDto(userEntity)).thenReturn(new IGRPUserDTO());

        // Act
        ResponseEntity<InvitationDTO> response = handler.handle(command);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userRepository).save(userEntity);
        verify(notificationAdapter).send(any());
        verify(commandBus).handle(any());
    }

    @Test
    void handle_rejectInvitation_success() {
        // Arrange
        UserInvitationResponseDTO dto = new UserInvitationResponseDTO();
        dto.setEmail("test@example.com");
        dto.setAccept(false);

        RespondUserInvitationCommand command = new RespondUserInvitationCommand(dto, token);

        when(userRepository.existsByEmail(dto.getEmail())).thenReturn(true);
        when(adapter.resolveUser(dto.getEmail())).thenReturn(Optional.of(userEntity));
        when(userRepository.findByExternalId(userEntity.getExternalId())).thenReturn(Optional.of(userEntity));
        when(userRepository.save(any())).thenReturn(userEntity);
        when(userMapper.toDto(userEntity)).thenReturn(new IGRPUserDTO());

        // Act
        ResponseEntity<InvitationDTO> response = handler.handle(command);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotEquals("test@example.com", userEntity.getEmail()); // email changed due to rejection
        verify(userRepository).save(userEntity);
        verifyNoInteractions(notificationAdapter); // no email should be sent
        verifyNoInteractions(commandBus); // no status update command
    }

    @Test
    void handle_userDoesNotExist_throwsException() {
        UserInvitationResponseDTO dto = new UserInvitationResponseDTO();
        dto.setEmail("unknown@example.com");
        dto.setAccept(true);
        RespondUserInvitationCommand command = new RespondUserInvitationCommand(dto, token);

        when(userRepository.existsByEmail(dto.getEmail())).thenReturn(false);

        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> handler.handle(command));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("was not invited already"));
    }

    @Test
    void handle_userNotInProvider_throwsException() {
        UserInvitationResponseDTO dto = new UserInvitationResponseDTO();
        dto.setEmail("test@example.com");
        dto.setAccept(true);
        RespondUserInvitationCommand command = new RespondUserInvitationCommand(dto, token);

        when(userRepository.existsByEmail(dto.getEmail())).thenReturn(true);
        when(adapter.resolveUser(dto.getEmail())).thenReturn(Optional.empty());

        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> handler.handle(command));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("was not found in the Identity Provider"));
    }
}
