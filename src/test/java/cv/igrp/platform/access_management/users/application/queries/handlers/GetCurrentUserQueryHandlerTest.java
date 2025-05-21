package cv.igrp.platform.access_management.users.application.queries.handlers;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import cv.igrp.platform.access_management.users.application.dto.*;
import cv.igrp.platform.access_management.users.application.queries.queries.*;

import java.util.Collections;
import java.util.List;

@ExtendWith(MockitoExtension.class)
public class GetCurrentUserQueryHandlerTest {

    @Mock
    private IGRPUserRepository userRepository;

    @Mock
    private IGRPUserMapper userMapper;

    @InjectMocks
    private GetCurrentUserQueryHandler getCurrentUserQueryHandler;

    private GetUsersQuery getUsersQuery(List<Integer> getUsersRequest, Integer applicationId, Integer departmentId, String name, String username, String email){
        return new GetUsersQuery(getUsersRequest, applicationId, departmentId, name, username, email);
    }

    private GetUsersQuery query;
    private IGRPUser user;
    private IGRPUserDTO dto;

    @BeforeEach
    void setUp() {
        user = new IGRPUser();
        user.setId(1);
        user.setName("Test");
        user.setUsername("test");
        user.setEmail("test@example.com");

        dto = new IGRPUserDTO();
        dto.setId(1);
        dto.setName("Test");
        dto.setUsername("test");
        dto.setEmail("test@example.com");
    }

    @Test
    @DisplayName("should return filtered users based on query")
    void testHandle_withValidQuery_shouldReturnFilteredUsers() {
        //Arrange
        query = getUsersQuery(null, 10, 20, "Test", "test", "test@example.com");
        when(userRepository.findAll(any(Specification.class))).thenReturn(List.of(user));
        when(userMapper.toDto(user)).thenReturn(dto);

        // Act
        ResponseEntity<List<IGRPUserDTO>> response = getUsersQueryHandler.handle(query);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(dto, response.getBody().getFirst());
        assertEquals(1, response.getBody().size());

        // Verify
        verify(userRepository, times(1)).findAll(any(Specification.class));
        verify(userMapper, times(1)).toDto(user);
        verifyNoMoreInteractions(userRepository, userMapper);
    }

    @Test
    @DisplayName("should return empty list if no users match")
    void testHandle_withNoMatch_shouldReturnEmptyList() {
        // Arrange
        query = getUsersQuery(null, 10, 20, "NotExist", "noone", "noone@example.com");
        when(userRepository.findAll(any(Specification.class))).thenReturn(Collections.emptyList());

        // Act
        ResponseEntity<List<IGRPUserDTO>> response = getUsersQueryHandler.handle(query);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isEmpty());

        // Verify
        verify(userRepository, times(1)).findAll(any(Specification.class));
        verifyNoInteractions(userMapper);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    @DisplayName("should return empty list when no users match query filters")
    void testHandle_whenNoUsersMatch_shouldReturnEmptyList() {
        // Arrange
        query = getUsersQuery(null, null, null, "", "", "");
        when(userRepository.findAll(any(Specification.class))).thenReturn(Collections.emptyList());

        // Act
        ResponseEntity<List<IGRPUserDTO>> response = getUsersQueryHandler.handle(query);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());

        // Verify
        verify(userRepository, times(1)).findAll(any(Specification.class));
        verifyNoInteractions(userMapper);
        verifyNoMoreInteractions(userRepository, userMapper);
      }

}