package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.auth.core.adapter.IAdapter;
import cv.igrp.framework.notifications.core.adapter.NotificationAdapter;
import cv.igrp.framework.notifications.core.exception.NotificationException;
import cv.igrp.framework.notifications.core.model.Notification;
import cv.igrp.framework.notifications.core.model.NotificationResult;
import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
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

    private IGRPUserDTO igrpUserDTO;
    private InviteUserCommand command;
    private IGRPUserEntity user;

    @BeforeEach
    void setUp() {
        igrpUserDTO = new IGRPUserDTO();
        igrpUserDTO.setName("John Doe");
        igrpUserDTO.setUsername("john");
        igrpUserDTO.setEmail("john@example.com");

        user = new IGRPUserEntity();

        user.setName("John Doe");
        user.setUsername("john");
        user.setEmail("john@example.com");

        command = new InviteUserCommand(igrpUserDTO);
    }

    /**
     * Test: should throw CONFLICT when username already exists in the repository.
     */
    @Test
    void itShouldThrowConflict_WhenUsernameAlreadyExists() {
        when(userRepository.existsByUsername("john")).thenReturn(true);

        IgrpResponseStatusException ex = assertThrows(
                IgrpResponseStatusException.class,
                () -> underTest.handle(command)
        );

        assertEquals(HttpStatus.CONFLICT.value(), ex.getBody().getStatus());
        assertTrue(ex.getMessage().contains("john"));
        verify(userRepository).existsByUsername("john");
        verifyNoInteractions(adapter);
        verifyNoInteractions(notificationAdapter);
    }

    /**
     * Test: should throw BAD_REQUEST when the IAM provider does not contain the user.
     */
    @Test
    void itShouldThrowBadRequest_WhenUserDoesNotExistInIAMProvider() {
        when(userRepository.existsByUsername("john")).thenReturn(false);
        when(adapter.resolveUser("john")).thenReturn(Optional.empty());

        IgrpResponseStatusException ex = assertThrows(
                IgrpResponseStatusException.class,
                () -> underTest.handle(command)
        );

        assertEquals(HttpStatus.BAD_REQUEST.value(), ex.getBody().getStatus());
        assertTrue(ex.getMessage().contains("does not exist"));
        verify(adapter).resolveUser("john");
        verify(userRepository, never()).save(any());
        verifyNoInteractions(notificationAdapter);
    }

    /**
     * Test: should invite the user successfully when all conditions are valid.
     */
    @Test
    void itShouldInviteUserSuccessfully_WhenUserExistsInIAMProvider() throws NotificationException {
        when(userRepository.existsByUsername("john")).thenReturn(false);
        when(adapter.resolveUser("john")).thenReturn(Optional.of(user));

        IGRPUserEntity savedEntity = new IGRPUserEntity();
        savedEntity.setId(1);
        savedEntity.setName("John Doe");
        savedEntity.setUsername("john");
        savedEntity.setEmail("john@example.com");

        when(userRepository.save(any(IGRPUserEntity.class))).thenReturn(savedEntity);

        IGRPUserDTO expectedDto = new IGRPUserDTO();
        expectedDto.setId(1);
        expectedDto.setName("John Doe");
        expectedDto.setUsername("john");
        expectedDto.setEmail("john@example.com");

        when(userMapper.toDto(savedEntity)).thenReturn(expectedDto);

        ResponseEntity<IGRPUserDTO> response = underTest.handle(command);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(expectedDto.getUsername(), response.getBody().getUsername());
        assertEquals(expectedDto.getEmail(), response.getBody().getEmail());

        verify(userRepository).existsByUsername("john");
        verify(adapter).resolveUser("john");
        verify(userRepository).save(any(IGRPUserEntity.class));
        verify(notificationAdapter).send(any(Notification.class));
        verify(userMapper).toDto(savedEntity);
    }
}