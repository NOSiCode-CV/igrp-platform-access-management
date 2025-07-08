package cv.igrp.platform.access_management.users.application.commands.handlers;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.IGRPUser;
import cv.igrp.platform.access_management.shared.domain.models.Role;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.IGRPUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import cv.igrp.platform.access_management.users.application.commands.commands.*;

import java.util.*;

@ExtendWith(MockitoExtension.class)
public class RemoveRolesFromUserCommandHandlerTest {

    @Mock
    IGRPUserRepository userRepository;

    @InjectMocks
    private RemoveRolesFromUserCommandHandler removeRolesFromUserCommandHandler;

    private RemoveRolesFromUserCommand removeRolesFromUserCommand(List<Integer> removeRolesFromUserRequest, Integer id){
        return new RemoveRolesFromUserCommand(removeRolesFromUserRequest, id);
    }

    private RemoveRolesFromUserCommand command;
    private List<Integer> idRolesToBeRemoved;
    private IGRPUser user;
    private Role role1, role2;

    private final Integer USER_ID = 1;

    @BeforeEach
    void setUp() {
        role1 = new Role();
        role1.setId(100);
        role1.setName("Admin");
        role1.setDescription("Admin role");

        role2 = new Role();
        role2.setId(200);
        role2.setName("User");
        role2.setDescription("User role");

        user = new IGRPUser();
        user.setId(USER_ID);
        user.setRoles(new ArrayList<>(List.of(role1, role2)));

        idRolesToBeRemoved = new ArrayList<>();
        idRolesToBeRemoved.add(100);
        idRolesToBeRemoved.add(200);
    }

    @Test
    @DisplayName("should remove matching role and return updated roles")
    void testHandle_whenRoleIdMatches_shouldRemoveAndReturnRemainingRoles() {
        // Arrange
        command = removeRolesFromUserCommand(new ArrayList<>(List.of(100)), USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        // Act
        ResponseEntity<List<RoleDTO>> response = removeRolesFromUserCommandHandler.handle(command);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<RoleDTO> result = response.getBody();
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(200, result.getFirst().getId());

        // Verify
        verify(userRepository, times(1)).findById(USER_ID);
        verify(userRepository, times(1)).save(user);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    @DisplayName("should do nothing if role ID doesn't match any roles")
    void testHandle_whenRoleIdDoesNotMatch_shouldReturnAllRoles() {
        // Arrange
        List<Integer> removeRolesThatDoesntExist = new ArrayList<>();
        removeRolesThatDoesntExist.add(500);
        removeRolesThatDoesntExist.add(700);

        command = removeRolesFromUserCommand(removeRolesThatDoesntExist,USER_ID);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        // Act
        ResponseEntity<List<RoleDTO>> response = removeRolesFromUserCommandHandler.handle(command);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<RoleDTO> roles = response.getBody();
        assertNotNull(roles);
        assertEquals(2, roles.size());
        assertEquals(100,roles.getFirst().getId());

        // Verify
        verify(userRepository, times(1)).findById(USER_ID);
        verify(userRepository, times(1)).save(user);
        verifyNoMoreInteractions(userRepository);

    }

    @Test
    @DisplayName("should throw exception if user not found")
    void testHandle_whenUserNotFound_shouldThrowException() {
        // Arrange
        command = removeRolesFromUserCommand(idRolesToBeRemoved, USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // Act
        IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class,
                () -> removeRolesFromUserCommandHandler.handle(command));

        // Assert
        assertNotNull(exception.getBody().getProperties());
        assertEquals("User not found with id: " + USER_ID, exception.getBody().getProperties().get("details"));

        // Verify
        verify(userRepository, times(1)).findById(USER_ID);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    @DisplayName("should return all roles when removeRolesFromUserRequest is empty")
    void testHandle_whenRoleIdsListIsEmpty_shouldReturnAllRoles() {
        // Arrange
        command = removeRolesFromUserCommand(new ArrayList<>(), USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        // Act
        ResponseEntity<List<RoleDTO>> response = removeRolesFromUserCommandHandler.handle(command);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, Objects.requireNonNull(response.getBody()).size());

        // Verify
        verify(userRepository, times(1)).findById(USER_ID);
        verify(userRepository, times(1)).save(user);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    @DisplayName("should return all roles when role removal list is null")
    void testHandle_whenRoleIdsListIsNull_shouldReturnAllRoles() {
        // Arrange
         command = new RemoveRolesFromUserCommand(null, USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        // Act
        ResponseEntity<List<RoleDTO>> response = removeRolesFromUserCommandHandler.handle(command);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, Objects.requireNonNull(response.getBody()).size());

        // Verify
        verify(userRepository, times(1)).findById(USER_ID);
        verify(userRepository, times(1)).save(user);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    @DisplayName("should return empty list if user has no roles assigned")
    void testHandle_whenUserHasNoRoles_shouldReturnEmptyList() {
        // Arrange
        user.setRoles(new ArrayList<>());
        command = removeRolesFromUserCommand(idRolesToBeRemoved, USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        // Act
        ResponseEntity<List<RoleDTO>> response = removeRolesFromUserCommandHandler.handle(command);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(Objects.requireNonNull(response.getBody()).isEmpty());

        // Verify
        verify(userRepository, times(1)).findById(USER_ID);
        verify(userRepository, times(1)).save(user);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    @DisplayName("should not remove any roles if no matching IDs found")
    void testHandle_whenRoleIdsDoNotMatch_shouldReturnUnchangedRoles() {
        // Arrange
        command = new RemoveRolesFromUserCommand(new ArrayList<>(List.of(999,899)), USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        // Act
        ResponseEntity<List<RoleDTO>> response = removeRolesFromUserCommandHandler.handle(command);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, Objects.requireNonNull(response.getBody()).size());

        // Verify
        verify(userRepository, times(1)).findById(USER_ID);
        verify(userRepository, times(1)).save(user);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    @DisplayName("should safely remove from immutable role list using defensive copy")
    void testHandle_whenRolesAreImmutable_shouldRemoveWithoutError() {
        // Arrange
        user.setRoles(List.of(role1, role2));
        command = removeRolesFromUserCommand( List.of(100), USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        // Act
        ResponseEntity<List<RoleDTO>> response = removeRolesFromUserCommandHandler.handle(command);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, Objects.requireNonNull(response.getBody()).size());
        assertEquals(200, response.getBody().getFirst().getId());

        // verify
        verify(userRepository, times(1)).findById(USER_ID);
        verify(userRepository, times(1)).save(user);
        verifyNoMoreInteractions(userRepository);
    }

}