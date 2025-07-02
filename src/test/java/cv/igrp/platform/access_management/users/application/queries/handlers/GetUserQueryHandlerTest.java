package cv.igrp.platform.access_management.users.application.queries.handlers;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.IGRPUser;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.IGRPUserRepository;
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
import cv.igrp.platform.access_management.users.application.queries.queries.*;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetUserQueryHandler Tests")
public class GetUserQueryHandlerTest {

    @Mock
    private IGRPUserRepository userRepository;

    @Mock
    private IGRPUserMapper userMapper;

    @InjectMocks
    private GetUserQueryHandler queryUserHandler;

    private GetUserQuery getUserQuery(Integer id){
        return new GetUserQuery(id);
    }

    private IGRPUser user;
    private IGRPUserDTO userDTO;
    private GetUserQuery query;

    private final Integer USER_ID = 1;

    @BeforeEach
    void setUp() {
        user = new IGRPUser();
        user.setId(USER_ID);
        user.setName("Test");
        user.setUsername("test");
        user.setEmail("test@example.com");

        userDTO = new IGRPUserDTO();
        userDTO.setId(USER_ID);
        userDTO.setName("Test");
        userDTO.setUsername("test");
        userDTO.setEmail("test@example.com");

        query = getUserQuery(USER_ID);
    }

    @Test
    @DisplayName("should return 200 OK with user when ID is valid")
    void testHandle_whenUserExists_shouldReturnUserDto() {
        // Arrange
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userMapper.toDto(user)).thenReturn(userDTO);

        // Act
        ResponseEntity<IGRPUserDTO> response = queryUserHandler.handle(query);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(userDTO, response.getBody());

        // Verify
        verify(userRepository, times(1)).findById(USER_ID);
        verify(userMapper, times(1)).toDto(user);
        verifyNoMoreInteractions(userRepository, userMapper);

    }

    @Test
    @DisplayName("should throw IgrpResponseStatusException when user does not exist")
    void testHandle_whenUserDoesNotExist_shouldThrowException() {
        // Arrange
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // Act
        IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class, () ->
                queryUserHandler.handle(query));

        // Assert
        assertNotNull(exception);
        assertEquals("User not found with id: " + USER_ID, exception.getBody().getDetail());

        // Verify
        verify(userRepository, times(1)).findById(USER_ID);
        verifyNoMoreInteractions(userRepository);
        verifyNoInteractions(userMapper);
    }

    @Test
    @DisplayName("should return 400 Bad Request when query ID is null")
    void testHandle_whenQueryIdIsNull_shouldReturnBadRequest() {
        // Arrange
        query = new GetUserQuery(null);

        // Act
        ResponseEntity<IGRPUserDTO> response = queryUserHandler.handle(query);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNull(response.getBody());

        // Verify
        verifyNoInteractions(userRepository, userMapper);
    }

}