package cv.igrp.platform.access_management.users.application.queries;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.users.application.commands.GetUsersCommand;
import cv.igrp.platform.access_management.users.application.commands.GetUsersCommandHandler;
import cv.igrp.platform.access_management.users.mapper.IGRPUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
public class GetUsersCommandHandlerTest {

    @Mock
    private IGRPUserEntityRepository userRepository;

    @Mock
    private IGRPUserMapper userMapper;

    @InjectMocks
    private GetUsersCommandHandler handler;

    private IGRPUserEntity mockUser;
    private IGRPUserDTO mockUserDTO;

    @BeforeEach
    void setUp() {
        mockUser = new IGRPUserEntity();
        mockUser.setId(1);
        mockUser.setName("John Doe");
        mockUser.setUsername("jdoe");
        mockUser.setEmail("jdoe@example.com");

        mockUserDTO = new IGRPUserDTO();
        mockUserDTO.setId(1);
        mockUserDTO.setName("John Doe");
        mockUserDTO.setUsername("jdoe");
        mockUserDTO.setEmail("jdoe@example.com");
    }

    @Test
    void testHandle_shouldReturnListOfUserDTOs() {
        // Given
        GetUsersCommand command = new GetUsersCommand(
                List.of(1),
                1,
                1,
                "John",
                "jdoe",
                "jdoe@example.com"
        );

        when(userRepository.findAll(any(Specification.class))).thenReturn(List.of(mockUser));
        when(userMapper.toDto(mockUser)).thenReturn(mockUserDTO);

        // When
        ResponseEntity<List<IGRPUserDTO>> response = handler.handle(command);

        // Then
        assertNotNull(response);
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("John Doe", response.getBody().getFirst().getName());
        verify(userRepository, times(1)).findAll(any(Specification.class));
        verify(userMapper, times(1)).toDto(mockUser);
    }
}