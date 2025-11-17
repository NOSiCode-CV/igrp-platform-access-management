package cv.igrp.platform.access_management.users.application.commands;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.framework.auth.core.adapter.IAdapter;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
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

import java.util.*;

@ExtendWith(MockitoExtension.class)
public class RemoveRolesFromUserCommandHandlerTest {

    @Mock
    IGRPUserEntityRepository userRepository;

    @Mock
    RoleEntityRepository roleRepository;

    @Mock
    IAdapter adapter;

    @InjectMocks
    private RemoveRolesFromUserCommandHandler removeRolesFromUserCommandHandler;

    private RemoveRolesFromUserCommand removeRolesFromUserCommand(List<String> removeRolesFromUserRequest, Integer id){
        return new RemoveRolesFromUserCommand(removeRolesFromUserRequest, id);
    }

    private RemoveRolesFromUserCommand command;
    private List<String> idRolesToBeRemoved;
    private IGRPUserEntity user;
    private RoleEntity role1, role2;
    private DepartmentEntity department;

    private final Integer USER_ID = 1;

    @BeforeEach
    void setUp() {

        department = new DepartmentEntity();
        department.setId(1);
        department.setName("Department 1");
        department.setCode("DEPT_1");
        department.setRoles(new ArrayList<>());

        role1 = new RoleEntity();
        role1.setId(100);
        role1.setCode("admin");
        role1.setDescription("Admin role");
        role1.setUsers(new HashSet<>());
        role1.setDepartment(department);

        role2 = new RoleEntity();
        role2.setId(200);
        role2.setCode("user");
        role2.setDescription("User role");
        role2.setUsers(new HashSet<>());
        role2.setDepartment(department);

        user = new IGRPUserEntity();
        user.setId(USER_ID);
        user.setRoles(new ArrayList<>());

        // Keep relationship consistent
        user.getRoles().add(role1);
        user.getRoles().add(role2);
        role1.getUsers().add(user);
        role2.getUsers().add(user);
        department.getRoles().add(role1);
        department.getRoles().add(role2);

        idRolesToBeRemoved = new ArrayList<>();
        idRolesToBeRemoved.add("admin");
        idRolesToBeRemoved.add("user");
    }


    @Test
    @DisplayName("should remove matching role and return updated roles")
    void testHandle_whenRoleIdMatches_shouldRemoveAndReturnRemainingRoles() {
        // Arrange
        command = removeRolesFromUserCommand(List.of("admin"), USER_ID);
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
        List<String> removeRolesThatDoesntExist = new ArrayList<>();
        removeRolesThatDoesntExist.add("auditor");
        removeRolesThatDoesntExist.add("tester");

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
        assertEquals("User not found with ID: " + USER_ID, exception.getBody().getProperties().get("details"));

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
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    @DisplayName("should return empty list if user has no roles assigned")
    void testHandle_whenUserHasNoRoles_shouldReturnEmptyList() {
        // Arrange
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
        command = new RemoveRolesFromUserCommand(new ArrayList<>(List.of("reporter","maintainer")), USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        // Act
        ResponseEntity<List<RoleDTO>> response = removeRolesFromUserCommandHandler.handle(command);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, Objects.requireNonNull(response.getBody()).size());

        // Verify
        verify(userRepository, times(1)).findById(USER_ID);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    @DisplayName("should safely remove from immutable role list using defensive copy")
    void testHandle_whenRolesAreImmutable_shouldRemoveWithoutError() {

        command = removeRolesFromUserCommand( List.of("admin"), USER_ID);
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