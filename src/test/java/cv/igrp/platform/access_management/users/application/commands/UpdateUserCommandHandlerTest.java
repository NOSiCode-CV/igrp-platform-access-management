package cv.igrp.platform.access_management.users.application.commands;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.users.mapper.IGRPUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class UpdateUserCommandHandlerTest {

    @Mock
    IGRPUserEntityRepository userRepository;

    @Mock
    IGRPUserMapper userMapper;

    @InjectMocks
    private UpdateUserCommandHandler updateUserCommandHandler;

    private IGRPUserEntity user;
    private IGRPUserDTO dto;
    private UpdateUserCommand command;

    private final Integer USER_ID = 1;

    private UpdateUserCommand updateUserCommand(IGRPUserDTO igrpuserdto, Integer id){
        return new UpdateUserCommand(igrpuserdto,id);
    }

    @BeforeEach
    void setUp() {
        user = new IGRPUserEntity();
        user.setId(USER_ID);
        user.setName("Old Name");
        user.setUsername("oldUser");
        user.setEmail("old@example.com");

        dto = new IGRPUserDTO();
        dto.setName("New Name");
        dto.setUsername("newUser");
        dto.setEmail("new@example.com");

        command = updateUserCommand(dto, USER_ID);
    }

    @Test
    @DisplayName("should update user and return updated DTO")
    void testHandle_whenUserExists_shouldUpdateAndReturnDto() {
        // Arrange
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toDto(user)).thenReturn(dto);

        // Act
        ResponseEntity<IGRPUserDTO> response = updateUserCommandHandler.handle(command);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getBody());
        assertEquals(dto, response.getBody());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("New Name", user.getName());
        assertEquals("newUser", user.getUsername());
        assertEquals("new@example.com", user.getEmail());

        // Verify
        verify(userRepository, times(1)).findById(USER_ID);
        verify(userRepository, times(1)).save(user);
        verify(userMapper, times(1)).toDto(user);
        verifyNoMoreInteractions(userMapper, userRepository);

    }

    @Test
    @DisplayName("should throw IgrpResponseStatusException if user does not exist")
    void testHandle_whenUserNotFound_shouldThrowException() {
        // Arrange
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // Act
        IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class, () ->
                updateUserCommandHandler.handle(command));

        // Assert
        assertNotNull(exception.getBody().getProperties());
        assertEquals("User not found with id: " + USER_ID, exception.getBody().getProperties().get("details"));

        // Verify
        verify(userRepository, times(1)).findById(USER_ID);
        verifyNoMoreInteractions(userRepository);
        verifyNoInteractions(userMapper);
    }

    @Test
    @DisplayName("should not overwrite existing user fields with null values from DTO")
    void testHandle_whenDtoHasNullFields_shouldNotOverwriteExistingUserFields() {
        // Arrange

        dto.setUsername(null);
        dto.setName(null);
        dto.setEmail("updated@example.com");

        command = updateUserCommand(dto, USER_ID);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toDto(user)).thenReturn(dto);

        // Act
        ResponseEntity<IGRPUserDTO> response = updateUserCommandHandler.handle(command);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getBody());
        assertEquals("updated@example.com", user.getEmail());
        assertEquals("oldUser", user.getUsername());
        assertEquals("Old Name", user.getName());
        assertEquals(HttpStatus.OK, response.getStatusCode());

        // Verify
        verify(userRepository, times(1)).findById(USER_ID);
        verify(userRepository, times(1)).save(user);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    @DisplayName("should not change any fields when all DTO fields are null")
    void testHandle_whenAllDtoFieldsAreNull_shouldNotChangeUser() {
        // Arrange
        dto.setName(null);
        dto.setUsername(null);
        dto.setEmail(null);

        command = updateUserCommand(dto, USER_ID);

        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toDto(user)).thenReturn(dto);

        // Act
        ResponseEntity<IGRPUserDTO> response = updateUserCommandHandler.handle(command);

        // Assert
        assertEquals("Old Name", user.getName());
        assertEquals("oldUser", user.getUsername());
        assertEquals("old@example.com", user.getEmail());
        assertEquals(HttpStatus.OK, response.getStatusCode());

        // Verify
        verify(userRepository, times(1)).findById(USER_ID);
        verify(userRepository, times(1)).save(user);
        verifyNoMoreInteractions(userRepository);
    }

}