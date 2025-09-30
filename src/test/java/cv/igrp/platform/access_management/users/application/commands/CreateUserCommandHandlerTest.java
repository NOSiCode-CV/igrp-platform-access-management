package cv.igrp.platform.access_management.users.application.commands;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.framework.auth.core.adapter.IAdapter;
import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.users.mapper.IGRPUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.ArrayList;
import java.util.Objects;

@ExtendWith(MockitoExtension.class)
public class CreateUserCommandHandlerTest {

    @Mock
    IGRPUserEntityRepository userRepository;

    @Mock
    IGRPUserMapper userMapper;

    @Mock
    @SuppressWarnings("unused")
    IAdapter iAdapter;

    @InjectMocks
    private CreateUserCommandHandler createUserCommandHandler;

    private IGRPUserDTO inputDto;
    private CreateUserCommand command;
    private IGRPUserEntity userEntity;
    private IGRPUserDTO expectedDto;

    private CreateUserCommand createUserCommand(IGRPUserDTO igrpuserdto){
        return new CreateUserCommand( igrpuserdto);
    }

    @BeforeEach
    void setUp() {
        inputDto = new IGRPUserDTO();
        inputDto.setName("Alice");
        inputDto.setUsername("alice123");
        inputDto.setEmail("alice@example.com");

        command = createUserCommand(inputDto);

        userEntity = new IGRPUserEntity();
        Integer USER_ID = 1;
        userEntity.setId(USER_ID);
        userEntity.setName("Alice");
        userEntity.setUsername("alice123");
        userEntity.setEmail("alice@example.com");
        userEntity.setRoles(new ArrayList<>());

        expectedDto = new IGRPUserDTO();
        expectedDto.setId(USER_ID);
        expectedDto.setName("Alice");
        expectedDto.setUsername("alice123");
        expectedDto.setEmail("alice@example.com");
    }

    @Test
    @DisplayName("should create user and return DTO in 200 OK response")
    @Disabled // TODO : fix this unit test later
    void testHandle_whenValidCommand_shouldReturnCreatedUserDto() {
      // Arrange
        when(userRepository.save(any(IGRPUserEntity.class))).thenReturn(userEntity);
        when(userMapper.toDto(userEntity)).thenReturn(expectedDto);
        when(userRepository.existsByUsername(expectedDto.getUsername())).thenReturn(false);

        // Act
        ResponseEntity<IGRPUserDTO> response = createUserCommandHandler.handle(command);

        // Assert
        assertNotNull(response);
        assertEquals(expectedDto, response.getBody());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        IGRPUserDTO actualDto = response.getBody();
        assert actualDto != null;
        assertEquals("Alice", actualDto.getName());
        assertEquals("alice123", actualDto.getUsername());
        assertEquals("alice@example.com", actualDto.getEmail());

        // Verify
        verify(userRepository, times(1)).save(any(IGRPUserEntity.class));
        verify(userMapper, times(1)).toDto(userEntity);
        verifyNoMoreInteractions(userRepository, userMapper);

    }

    @Test
    @DisplayName("should throw NullPointerException when IGRPUserCreateDTO is null")
    void testHandle_whenUserDtoIsNull_shouldThrowNPE() {
        // Arrange
        CreateUserCommand commandWithNullDto = createUserCommand(null);

        // Act & Assert
        assertThrows(NullPointerException.class, () -> createUserCommandHandler.handle(commandWithNullDto));

        verifyNoInteractions(userRepository, userMapper);
    }

    @Test
    @DisplayName("should still persist user if email is syntactically invalid (no validation in handler)")
    @Disabled //TODO: fix this unit test later
    void testHandle_whenEmailIsInvalidFormat_shouldStillPersistUser() {
        // Arrange
        inputDto.setEmail("not-an-email");
        userEntity.setEmail("not-an-email");
        expectedDto.setEmail("not-an-email");

        when(userRepository.save(any(IGRPUserEntity.class))).thenReturn(userEntity);
        when(userMapper.toDto(userEntity)).thenReturn(expectedDto);

        // Act
        ResponseEntity<IGRPUserDTO> response = createUserCommandHandler.handle(command);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("not-an-email", (Objects.requireNonNull(response.getBody())).getEmail());
    }

}