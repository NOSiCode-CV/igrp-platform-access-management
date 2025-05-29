package cv.igrp.platform.access_management.users.application.queries.handlers;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import cv.igrp.platform.access_management.shared.domain.models.IGRPUser;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.IGRPUserRepository;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
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
@DisplayName("GetCurrentUserQueryHandler Test")
class GetCurrentUserQueryHandlerTest {

    @Mock
    private IGRPUserRepository userRepository;

    @Mock
    private IGRPUserMapper userMapper;

    @Mock
    private AuthenticationHelper authenticationHelper;

    @InjectMocks
    private GetCurrentUserQueryHandler handler;

    private IGRPUser mockUser;
    private IGRPUserDTO mockDto;
    private final String mockUsername = "john.doe";

    @BeforeEach
    void setUp() {
        mockUser = new IGRPUser();
        mockUser.setId(1);
        mockUser.setUsername(mockUsername);
        mockUser.setName("John Doe");
        mockUser.setEmail("john@example.com");

        mockDto = new IGRPUserDTO();
        mockDto.setId(1);
        mockDto.setUsername(mockUsername);
        mockDto.setName("John Doe");
        mockDto.setEmail("john@example.com");
    }

    @Test
    @DisplayName("Should return user DTO when authenticated user is found")
    void testHandle_whenUserExists_shouldReturnUserDTO() {
        // Arrange
        when(authenticationHelper.getPreferredUsername()).thenReturn(mockUsername);
        when(userRepository.findByUsername(mockUsername)).thenReturn(Optional.of(mockUser));
        when(userMapper.toDto(mockUser)).thenReturn(mockDto);

        // Act
        ResponseEntity<IGRPUserDTO> response = handler.handle(new GetCurrentUserQuery());

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(mockDto.getUsername(), response.getBody().getUsername());
        assertEquals(mockDto.getEmail(), response.getBody().getEmail());

        // Verify
        verify(authenticationHelper, times(1)).getPreferredUsername();
        verify(userRepository, times(1)).findByUsername(mockUsername);
        verify(userMapper, times(1)).toDto(mockUser);
    }

    @Test
    @DisplayName("Should return 404 when authenticated user is not found")
    void testHandle_whenUserNotFound_shouldReturnNotFound() {
        // Arrange
        when(authenticationHelper.getPreferredUsername()).thenReturn(mockUsername);
        when(userRepository.findByUsername(mockUsername)).thenReturn(Optional.empty());

        // Act
        ResponseEntity<IGRPUserDTO> response = handler.handle(new GetCurrentUserQuery());

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());

        // Verify
        verify(authenticationHelper, times(1)).getPreferredUsername();
        verify(userRepository, times(1)).findByUsername(mockUsername);
        verifyNoInteractions(userMapper);
    }
}