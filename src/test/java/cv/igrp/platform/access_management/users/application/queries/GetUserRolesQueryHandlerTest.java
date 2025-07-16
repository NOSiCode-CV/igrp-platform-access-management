package cv.igrp.platform.access_management.users.application.queries;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetUserRolesQueryHandler Tests")
public class GetUserRolesQueryHandlerTest {

    @Mock
    private IGRPUserEntityRepository userRepository;

    @Mock
    private RoleMapper roleMapper;


    @InjectMocks
    private GetUserRolesQueryHandler getUserRolesQueryHandler;

    private GetUserRolesQuery getUserRolesQuery(Integer id){
     return new GetUserRolesQuery(1, id);
    }

    private GetUserRolesQuery query;
    private IGRPUserEntity user;
    private RoleEntity role1, role2;
    private RoleDTO roleDto1, roleDto2;

    private final Integer USER_ID = 1;

    @BeforeEach
    void setUp() {
        user = new IGRPUserEntity();
        user.setId(USER_ID);
        user.setRoles(new ArrayList<>());

        role1 = new RoleEntity();
        role1.setId(100);
        role1.setName("Admin");
        role1.setDescription("Admin Role");

        role2 = new RoleEntity();
        role2.setId(200);
        role2.setName("User");
        role2.setDescription("User Role");

        roleDto1 = new RoleDTO(100, "Admin", "Admin Role", null, null, null);
        roleDto2 = new RoleDTO(200, "User", "User Role", null, null, null);

    }

    @Test
    @DisplayName("handle(): should return user roles when user exists")
    void handle_whenUserHasRoles_shouldReturnRoleDTOList() {
        // Arrange
        user.setRoles(List.of(role1, role2));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(roleMapper.mapToDto(role1)).thenReturn(roleDto1);
        when(roleMapper.mapToDto(role2)).thenReturn(roleDto2);

        query = getUserRolesQuery(USER_ID);

        // Act
        ResponseEntity<List<RoleDTO>> response = getUserRolesQueryHandler.handle(query);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains(roleDto1));
        assertTrue(response.getBody().contains(roleDto2));

        // Verify
        verify(userRepository, times(1)).findById(USER_ID);
        verify(roleMapper, times(1)).mapToDto(role1);
        verify(roleMapper, times(1)).mapToDto(role2);
        verifyNoMoreInteractions(userRepository, roleMapper);

    }

    @Test
    @DisplayName("should return empty list when user has no roles")
    void testHandle_whenUserHasNoRoles_shouldReturnEmptyList() {
        //Arrange
        user.setRoles(Collections.unmodifiableList(new ArrayList<>()));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        query = getUserRolesQuery(USER_ID);

        // Act
        ResponseEntity<List<RoleDTO>> response = getUserRolesQueryHandler.handle(query);

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().size());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isEmpty());

        // Verify
        verify(userRepository, times(1)).findById(USER_ID);
        verifyNoMoreInteractions(userRepository, roleMapper);
    }

    @Test
    @DisplayName("should throw IgrpResponseStatusException when user is not found")
    void testHandle_whenUserNotFound_shouldThrowEntityNotFoundException() {
        // Arrange
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        query = getUserRolesQuery(USER_ID);

        // Act
        IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class, () ->
                getUserRolesQueryHandler.handle(query));
        // Assert
        assertNotNull(exception.getBody().getProperties());
        assertEquals("User not found with id: " + USER_ID, exception.getBody().getProperties().get("details"));

        // Verify
        verify(userRepository, times(1)).findById(USER_ID);
        verifyNoInteractions(roleMapper);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    @DisplayName("should skip roles when mapper returns null")
    void testHandle_whenMapperReturnsNull_shouldIgnoreThatRole() {
        // Arrange
        user.setRoles(new ArrayList<>(List.of(role1, role2)));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(roleMapper.mapToDto(role1)).thenReturn(roleDto1);
        when(roleMapper.mapToDto(role2)).thenReturn(null);

        query = getUserRolesQuery(USER_ID);

        // Act
        ResponseEntity<List<RoleDTO>> response = getUserRolesQueryHandler.handle(query);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains(roleDto1));

        // Verify
        verify(userRepository, times(1)).findById(USER_ID);
        verify(roleMapper, times(1)).mapToDto(role1);
        verify(roleMapper, times(1)).mapToDto(role2);
        verifyNoMoreInteractions(userRepository, roleMapper);

    }
}