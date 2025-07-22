package cv.igrp.platform.access_management.users.application.commands;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.application.dto.RoleUserDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
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
@DisplayName("AddRolesToUserCommandHandler Tests")
public class AddRolesToUserCommandHandlerTest {

    @Mock
    IGRPUserEntityRepository userRepository;

    @Mock
    RoleEntityRepository roleRepository;

    @Mock
    RoleMapper roleMapper;

    @InjectMocks
    private AddRolesToUserCommandHandler addRolesToUserCommandHandler;

    private IGRPUserEntity user;
    private RoleEntity role;
    private RoleDTO roleDTO;
    private AddRolesToUserCommand command;
    private final String USER_ID = "johndoe";
    private final String ROLE_ID = "admin";

    private AddRolesToUserCommand addRolesToUserCommand(RoleUserDTO roleUserDTO, String username) {
       return command = new AddRolesToUserCommand(roleUserDTO, username);
    }


    @BeforeEach
    void setUp() {
        user = new IGRPUserEntity();
        user.setUsername(USER_ID);

        role = new RoleEntity();
        role.setName(ROLE_ID);
        role.setUsers(null);

        roleDTO = new RoleDTO();
        roleDTO.setName(ROLE_ID);

        RoleUserDTO dto = new RoleUserDTO(USER_ID,ROLE_ID);

        command = addRolesToUserCommand(dto, USER_ID);
    }

    @Test
    @DisplayName("should add role to user and return 201 with RoleDTO")
    void testHandle_whenValidCommand_shouldReturnCreatedRoleDTO() {
        // Arrange
        when(userRepository.findByUsername(USER_ID)).thenReturn(Optional.of(user));
        when(roleRepository.findByName(ROLE_ID)).thenReturn(Optional.of(role));

        RoleEntity updatedRole = new RoleEntity();
        updatedRole.setName(ROLE_ID);
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
        verify(userRepository, times(1)).findByUsername(USER_ID);
        verify(roleRepository, times(1)).findByName(ROLE_ID);
        verify(roleRepository, times(1)).save(any(RoleEntity.class));
        verify(roleMapper, times(1)).mapToDto(updatedRole);
        verifyNoMoreInteractions(userRepository, roleRepository, roleMapper);
    }

    @Test
    @DisplayName("should throw IgrpResponseStatusException if user not found")
    void testHandle_whenUserNotFound_shouldThrowException() {
        // Arrange
        when(userRepository.findByUsername(USER_ID)).thenReturn(Optional.empty());

        // Act
        IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class, () ->
                addRolesToUserCommandHandler.handle(command));

        // Assert

        assertNotNull(exception.getBody().getProperties());
        assertEquals("User not found with id: " + USER_ID, exception.getBody().getProperties().get("details"));

        // Verify
        verify(userRepository, times(1)).findByUsername(USER_ID);
        verifyNoMoreInteractions(userRepository);
        verifyNoInteractions(roleRepository, roleMapper);
    }

    @Test
    @DisplayName("should throw IgrpResponseStatusException if role not found")
    void testHandle_whenRoleNotFound_shouldThrowException() {
        // Arrange
        when(userRepository.findByUsername(USER_ID)).thenReturn(Optional.of(user));
        when(roleRepository.findByName(ROLE_ID)).thenReturn(Optional.empty());

        // Act
        IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class, () ->
                addRolesToUserCommandHandler.handle(command));

        // Assert
        assertNotNull(exception.getBody().getProperties());
        assertEquals("Role not found with id: " + ROLE_ID, exception.getBody().getProperties().get("details"));

        // Verify
        verify(userRepository, times(1)).findByUsername(USER_ID);
        verify(roleRepository, times(1)).findByName(ROLE_ID);
        verifyNoMoreInteractions(userRepository,roleRepository);
        verifyNoInteractions(roleMapper);

    }

    @Test
    @DisplayName("should not duplicate user if already present in role")
    void testHandle_whenUserAlreadyInRole_shouldNotDuplicate() {
        // Arrange
        role.setUsers(new HashSet<>(List.of(user)));

        when(roleRepository.findByName(ROLE_ID)).thenReturn(Optional.of(role));
        when(userRepository.findByUsername(USER_ID)).thenReturn(Optional.of(user));
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
        verify(userRepository, times(1)).findByUsername(USER_ID);
        verify(roleRepository, times(1)).findByName(ROLE_ID);
        verify(roleMapper, times(1)).mapToDto(role);
        verify(roleRepository, times(1)).save(role);
        verifyNoMoreInteractions(userRepository,roleRepository,roleMapper);
    }

    @Test
    @DisplayName("should add user to existing users in role")
    void testHandle_whenRoleHasOtherUsers_shouldAddNewUser() {
        // Arrange
        IGRPUserEntity anotherUser = new IGRPUserEntity();
        anotherUser.setId(50);
        role.setUsers(new HashSet<>(List.of(anotherUser)));

        when(roleRepository.findByName(ROLE_ID)).thenReturn(Optional.of(role));
        when(userRepository.findByUsername(USER_ID)).thenReturn(Optional.of(user));
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
        verify(userRepository, times(1)).findByUsername(USER_ID);
        verify(roleRepository, times(1)).findByName(ROLE_ID);
        verify(roleMapper, times(1)).mapToDto(role);
        verify(roleRepository, times(1)).save(role);
        verifyNoMoreInteractions(userRepository,roleRepository,roleMapper);
    }

    @Test
    @DisplayName("should initialize user set when role has null users")
    void handle_whenRoleUsersIsNull_shouldInitializeSet() {
        // Arrange
        role.setUsers(null);

        when(userRepository.findByUsername(USER_ID)).thenReturn(Optional.of(user));
        when(roleRepository.findByName(ROLE_ID)).thenReturn(Optional.of(role));
        when(roleRepository.save(any(RoleEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleMapper.mapToDto(role)).thenReturn(roleDTO);

        // Act
        ResponseEntity<List<RoleDTO>> response = addRolesToUserCommandHandler.handle(command);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(role.getUsers());
        assertTrue(role.getUsers().contains(user));

        // Verify
        verify(userRepository, times(1)).findByUsername(USER_ID);
        verify(roleRepository, times(1)).findByName(ROLE_ID);
        verify(roleMapper, times(1)).mapToDto(role);
        verify(roleRepository, times(1)).save(role);
        verifyNoMoreInteractions(userRepository,roleRepository,roleMapper);
    }

}