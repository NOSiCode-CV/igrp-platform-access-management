package cv.igrp.platform.access_management.users.application.commands.handlers;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.application.dto.RoleUserDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.IGRPUser;
import cv.igrp.platform.access_management.shared.domain.models.Role;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.IGRPUserRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.RoleRepository;
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
@DisplayName("AddRolesToUserCommandHandler Tests")
public class AddRolesToUserCommandHandlerTest {

    @Mock
    IGRPUserRepository userRepository;

    @Mock
    RoleRepository roleRepository;

    @Mock
    RoleMapper roleMapper;

    @InjectMocks
    private AddRolesToUserCommandHandler addRolesToUserCommandHandler;

    private IGRPUser user;
    private Role role;
    private RoleDTO roleDTO;
    private AddRolesToUserCommand command;
    private final Integer USER_ID = 1;
    private final Integer ROLE_ID = 100;

    private AddRolesToUserCommand addRolesToUserCommand(RoleUserDTO roleUserDTO, Integer id) {
       return command = new AddRolesToUserCommand(roleUserDTO, id);
    }


    @BeforeEach
    void setUp() {
        user = new IGRPUser();
        user.setId(USER_ID);

        role = new Role();
        role.setId(ROLE_ID);
        role.setUsers(null);

        roleDTO = new RoleDTO();
        roleDTO.setId(ROLE_ID);
        roleDTO.setName("ADMIN");

        RoleUserDTO dto = new RoleUserDTO(USER_ID,ROLE_ID);

        command = addRolesToUserCommand(dto, USER_ID);
    }

    @Test
    @DisplayName("should add role to user and return 201 with RoleDTO")
    void testHandle_whenValidCommand_shouldReturnCreatedRoleDTO() {
        // Arrange
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));

        Role updatedRole = new Role();
        updatedRole.setId(ROLE_ID);
        updatedRole.setUsers(Set.of(user));
        when(roleRepository.save(any())).thenReturn(updatedRole);
        when(roleMapper.mapToDto(updatedRole)).thenReturn(roleDTO);

        // Act
        ResponseEntity<List<RoleDTO>> response = addRolesToUserCommandHandler.handle(command);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(1, Objects.requireNonNull(response.getBody()).size());
        assertEquals(roleDTO, response.getBody().getFirst());

        // Verify
        verify(userRepository, times(1)).findById(USER_ID);
        verify(roleRepository, times(1)).findById(ROLE_ID);
        verify(roleRepository, times(1)).save(any(Role.class));
        verify(roleMapper, times(1)).mapToDto(updatedRole);
        verifyNoMoreInteractions(userRepository, roleRepository, roleMapper);
    }

    @Test
    @DisplayName("should throw IgrpResponseStatusException if user not found")
    void testHandle_whenUserNotFound_shouldThrowException() {
        // Arrange
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // Act
        IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class, () ->
                addRolesToUserCommandHandler.handle(command));

        // Assert
        assertNotNull(exception);
        assertEquals("User not found with id: " + USER_ID, exception.getProblem().getDetails());

        // Verify
        verify(userRepository, times(1)).findById(USER_ID);
        verifyNoMoreInteractions(userRepository);
        verifyNoInteractions(roleRepository, roleMapper);
    }

    @Test
    @DisplayName("should throw IgrpResponseStatusException if role not found")
    void testHandle_whenRoleNotFound_shouldThrowException() {
        // Arrange
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.empty());

        // Act
        IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class, () ->
                addRolesToUserCommandHandler.handle(command));

        // Assert
        assertNotNull(exception);
        assertEquals("Role not found with id: " + ROLE_ID, exception.getProblem().getDetails());

        // Verify
        verify(userRepository, times(1)).findById(USER_ID);
        verify(roleRepository, times(1)).findById(ROLE_ID);
        verifyNoMoreInteractions(userRepository,roleRepository);
        verifyNoInteractions(roleMapper);

    }

    @Test
    @DisplayName("should not duplicate user if already present in role")
    void testHandle_whenUserAlreadyInRole_shouldNotDuplicate() {
        // Arrange
        role.setUsers(new HashSet<>(List.of(user)));

        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(roleRepository.save(role)).thenReturn(role);
        when(roleMapper.mapToDto(role)).thenReturn(roleDTO);

        // Act
        ResponseEntity<List<RoleDTO>> response = addRolesToUserCommandHandler.handle(command);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(1, Objects.requireNonNull(response.getBody()).size());
        assertEquals(roleDTO, response.getBody().getFirst());
        assertTrue(role.getUsers().contains(user));

        // Verify
        verify(userRepository, times(1)).findById(USER_ID);
        verify(roleRepository, times(1)).findById(ROLE_ID);
        verify(roleMapper, times(1)).mapToDto(role);
        verify(roleRepository, times(1)).save(role);
        verifyNoMoreInteractions(userRepository,roleRepository,roleMapper);
    }

    @Test
    @DisplayName("should add user to existing users in role")
    void testHandle_whenRoleHasOtherUsers_shouldAddNewUser() {
        // Arrange
        IGRPUser anotherUser = new IGRPUser();
        anotherUser.setId(50);
        role.setUsers(new HashSet<>(List.of(anotherUser)));

        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(roleRepository.save(role)).thenReturn(role);
        when(roleMapper.mapToDto(role)).thenReturn(roleDTO);

        // Act
        ResponseEntity<List<RoleDTO>> response = addRolesToUserCommandHandler.handle(command);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(1, Objects.requireNonNull(response.getBody()).size());
        assertTrue(role.getUsers().contains(anotherUser));
        assertTrue(role.getUsers().contains(user));

        // Verify
        verify(userRepository, times(1)).findById(USER_ID);
        verify(roleRepository, times(1)).findById(ROLE_ID);
        verify(roleMapper, times(1)).mapToDto(role);
        verify(roleRepository, times(1)).save(role);
        verifyNoMoreInteractions(userRepository,roleRepository,roleMapper);
    }

    @Test
    @DisplayName("should initialize user set when role has null users")
    void handle_whenRoleUsersIsNull_shouldInitializeSet() {
        // Arrange
        role.setUsers(null);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleMapper.mapToDto(role)).thenReturn(roleDTO);

        // Act
        ResponseEntity<List<RoleDTO>> response = addRolesToUserCommandHandler.handle(command);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(role.getUsers());
        assertTrue(role.getUsers().contains(user));

        // Verify
        verify(userRepository, times(1)).findById(USER_ID);
        verify(roleRepository, times(1)).findById(ROLE_ID);
        verify(roleMapper, times(1)).mapToDto(role);
        verify(roleRepository, times(1)).save(role);
        verifyNoMoreInteractions(userRepository,roleRepository,roleMapper);
    }

}