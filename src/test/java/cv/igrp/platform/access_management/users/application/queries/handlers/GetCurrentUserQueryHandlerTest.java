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
import cv.igrp.platform.access_management.shared.application.dto.*;
import cv.igrp.platform.access_management.users.application.queries.queries.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class GetCurrentUserQueryHandlerTest {

    @Mock
    private IGRPUserRepository userRepository;

    @Mock
    private IGRPUserMapper userMapper;

    @InjectMocks
    private GetCurrentUserQueryHandler handler;

    private IGRPUser user;
    private IGRPUserDTO dto;

    @BeforeEach
    void setUp() {
        user = new IGRPUser();
        user.setId(1);
        user.setName("Test User");
        user.setUsername("test");
        user.setEmail("test@example.com");

        dto = new IGRPUserDTO();
        dto.setId(1);
        dto.setName("Test User");
        dto.setUsername("test");
        dto.setEmail("test@example.com");
    }

    @Test
    @DisplayName("should return current user if found by ID")
    void testHandle_userExists_shouldReturnUserDTO() {
        // Mock o ID retornado de SecurityUtils
        Integer mockedUserId = 1;

        // Mock repository e mapper
        when(userRepository.findById(mockedUserId)).thenReturn(Optional.of(user));
        when(userMapper.toDto(user)).thenReturn(dto);

        // Act
        ResponseEntity<IGRPUserDTO> response = handler.handle(new GetCurrentUserQuery() {
            // simula pegar userId de um util — poderia ser injetado num mockable SecurityUtils também
        });

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Test User", response.getBody().getName());

        verify(userRepository, times(1)).findById(mockedUserId);
        verify(userMapper, times(1)).toDto(user);
    }

    @Test
    @DisplayName("should return 404 if user is not found")
    void testHandle_userNotFound_shouldReturn404() {
        Integer mockedUserId = 99;

        when(userRepository.findById(mockedUserId)).thenReturn(Optional.empty());

        ResponseEntity<IGRPUserDTO> response = handler.handle(new GetCurrentUserQuery());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());

        verify(userRepository, times(1)).findById(mockedUserId);
        verifyNoInteractions(userMapper);
    }
}