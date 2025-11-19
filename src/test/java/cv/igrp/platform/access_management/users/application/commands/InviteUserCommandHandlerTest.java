package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.auth.core.adapter.IAdapter;
import cv.igrp.framework.notifications.core.adapter.NotificationAdapter;
import cv.igrp.framework.notifications.core.exception.NotificationException;
import cv.igrp.framework.notifications.core.model.Notification;
import cv.igrp.framework.notifications.core.model.NotificationResult;
import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import cv.igrp.platform.access_management.shared.application.dto.InviteUserDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.users.mapper.IGRPUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InviteUserCommandHandlerTest {

    @Mock
    private NotificationAdapter<NotificationResult> notificationAdapter;

    @Mock
    private IGRPUserEntityRepository userRepository;

    @Mock
    private IGRPUserMapper userMapper;

    @Mock
    private IAdapter adapter;

    @InjectMocks
    private InviteUserCommandHandler underTest;

    private InviteUserDTO igrpUserDTO;
    private InviteUserCommand command;
    private IGRPUserEntity user;

    @BeforeEach
    void setUp() {
        igrpUserDTO = new InviteUserDTO();
        igrpUserDTO.setEmail("john@nosi.cv");

        user = new IGRPUserEntity();

        user.setName("John Doe");
        user.setUsername("john");
        user.setEmail("john@nosi.cv");

        command = new InviteUserCommand(igrpUserDTO);
    }

    /**
     * Test: should throw CONFLICT when email already exists in the repository.
     */
    @Test
    void itShouldThrowConflict_WhenUsernameAlreadyExists() {
        when(userRepository.existsByEmail("john@nosi.cv")).thenReturn(true);

        IgrpResponseStatusException ex = assertThrows(
                IgrpResponseStatusException.class,
                () -> underTest.handle(command)
        );

        assertEquals(HttpStatus.CONFLICT.value(), ex.getBody().getStatus());
        assertTrue(ex.getMessage().contains("john@nosi.cv"));
        verify(userRepository).existsByEmail("john@nosi.cv");
        verifyNoInteractions(adapter);
        verifyNoInteractions(notificationAdapter);
    }

    /**
     * Test: should throw BAD_REQUEST when the IAM provider does not contain the user.
     */
    @Test
    void itShouldThrowBadRequest_WhenUserDoesNotExistInIAMProvider() {
        when(userRepository.existsByEmail("john@nosi.cv")).thenReturn(false);
        when(adapter.resolveUser("john@nosi.cv")).thenReturn(Optional.empty());

        IgrpResponseStatusException ex = assertThrows(
                IgrpResponseStatusException.class,
                () -> underTest.handle(command)
        );

        assertEquals(HttpStatus.BAD_REQUEST.value(), ex.getBody().getStatus());
        assertTrue(ex.getMessage().contains("does not exist"));
        verify(adapter).resolveUser("john@nosi.cv");
        verify(userRepository, never()).save(any());
        verifyNoInteractions(notificationAdapter);
    }

    /**
     * Test: should invite the user successfully when all conditions are valid.
     */
    @Test
    void itShouldInviteUserSuccessfully_WhenUserExistsInIAMProvider() throws NotificationException {
        when(userRepository.existsByEmail("john@nosi.cv")).thenReturn(false);
        when(adapter.resolveUser("john@nosi.cv")).thenReturn(Optional.of(user));

        IGRPUserEntity savedEntity = new IGRPUserEntity();
        savedEntity.setId(1);
        savedEntity.setName("John Doe");
        savedEntity.setExternalId("f8c3de3d-1fea-4d7c-a8b0-29f63c4c3454");
        savedEntity.setEmail("john@nosi.cv");

        when(userRepository.save(any(IGRPUserEntity.class))).thenReturn(savedEntity);

        IGRPUserDTO expectedDto = new IGRPUserDTO();
        expectedDto.setId(1);
        expectedDto.setName("John Doe");
        expectedDto.setEmail("john@nosi.cv");

        when(userMapper.toDto(savedEntity)).thenReturn(expectedDto);

        ResponseEntity<IGRPUserDTO> response = underTest.handle(command);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(expectedDto.getUsername(), response.getBody().getUsername());
        assertEquals(expectedDto.getEmail(), response.getBody().getEmail());

        verify(userRepository).existsByEmail("john@nosi.cv");
        verify(adapter).resolveUser("john@nosi.cv");
        verify(userRepository).save(any(IGRPUserEntity.class));
        verify(notificationAdapter).send(any(Notification.class));
        verify(userMapper).toDto(savedEntity);
    }
}