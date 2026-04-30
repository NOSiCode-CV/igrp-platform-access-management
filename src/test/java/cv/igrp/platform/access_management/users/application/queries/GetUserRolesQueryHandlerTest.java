package cv.igrp.platform.access_management.users.application.queries;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.UserRoleAssignmentRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.UserRoleAssignment;
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

    @Mock
    private UserRoleAssignmentRepository userRoleAssignmentRepository;


    @InjectMocks
    private GetUserRolesQueryHandler getUserRolesQueryHandler;

    private GetUserRolesQuery getUserRolesQuery(Integer id){
     return new GetUserRolesQuery(id);
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

        role1 = new RoleEntity();
        role1.setId(100);
        role1.setCode("Admin");
        role1.setName("Admin");
        role1.setDescription("Admin Role");

        role2 = new RoleEntity();
        role2.setId(200);
        role2.setCode("User");
        role2.setName("User");
        role2.setDescription("User Role");

        roleDto1 = new RoleDTO(100, "Admin", "Admin", "Admin Role", null, null, null, null, null, List.of());
        roleDto2 = new RoleDTO(200, "User", "User", "User Role", null, null, null, null, null, List.of());
    }

    @Test
    @DisplayName("handle(): should return user roles when user exists")
    void handle_whenUserHasRoles_shouldReturnRoleDTOList() {
        // Arrange
        UserRoleAssignment ura1 = new UserRoleAssignment(user, role1, null);
        UserRoleAssignment ura2 = new UserRoleAssignment(user, role2, null);
        var assignments = List.of(ura1, ura2);
        
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRoleAssignmentRepository.findActiveByUserId(USER_ID)).thenReturn(assignments);
        when(roleMapper.mapToDto(ura1)).thenReturn(roleDto1);
        when(roleMapper.mapToDto(ura2)).thenReturn(roleDto2);

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
        verify(userRoleAssignmentRepository, times(1)).findActiveByUserId(USER_ID);
        verify(roleMapper, times(1)).mapToDto(ura1);
        verify(roleMapper, times(1)).mapToDto(ura2);
        verifyNoMoreInteractions(userRepository, userRoleAssignmentRepository, roleMapper);
    }

    @Test
    @DisplayName("should return empty list when user has no roles")
    void testHandle_whenUserHasNoRoles_shouldReturnEmptyList() {
        //Arrange
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRoleAssignmentRepository.findActiveByUserId(USER_ID)).thenReturn(Collections.emptyList());

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
        verify(userRoleAssignmentRepository, times(1)).findActiveByUserId(USER_ID);
        verifyNoMoreInteractions(userRepository, userRoleAssignmentRepository, roleMapper);
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
        assertEquals("User not found with ID: " + USER_ID, exception.getBody().getProperties().get("details"));

        // Verify
        verify(userRepository, times(1)).findById(USER_ID);
        verifyNoInteractions(roleMapper);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    @DisplayName("should skip roles when mapper returns null")
    void testHandle_whenMapperReturnsNull_shouldIgnoreThatRole() {
        // Arrange
        UserRoleAssignment ura1 = new UserRoleAssignment(user, role1, null);
        UserRoleAssignment ura2 = new UserRoleAssignment(user, role2, null);
        var assignments = List.of(ura1, ura2);
        
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRoleAssignmentRepository.findActiveByUserId(USER_ID)).thenReturn(assignments);
        when(roleMapper.mapToDto(ura1)).thenReturn(roleDto1);
        when(roleMapper.mapToDto(ura2)).thenReturn(null);

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
        verify(userRoleAssignmentRepository, times(1)).findActiveByUserId(USER_ID);
        verify(roleMapper, times(1)).mapToDto(ura1);
        verify(roleMapper, times(1)).mapToDto(ura2);
        verifyNoMoreInteractions(userRepository, userRoleAssignmentRepository, roleMapper);
    }
}