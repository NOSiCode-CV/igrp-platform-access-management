package cv.igrp.platform.access_management.users.application.commands;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.framework.auth.core.adapter.IAdapter;
import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.exceptions.NoActionPerformedException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
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

    @Mock
    private IAdapter adapter;

    @InjectMocks
    private AddRolesToUserCommandHandler addRolesToUserCommandHandler;

    private IGRPUserEntity user;
    private RoleEntity role;
    private RoleDTO roleDTO;
    private final String USERNAME = "johndoe";
    private final String ROLE_NAME = "admin";

    @BeforeEach
    void setUp() {
        user = new IGRPUserEntity();
        user.setUsername(USERNAME);
        user.setRoles(new ArrayList<>());

        role = new RoleEntity();
        role.setName(ROLE_NAME);
        role.setUsers(null);

        DepartmentEntity department = new DepartmentEntity();
        department.setCode("DEP-001");
        role.setDepartment(department);

        roleDTO = new RoleDTO();
        roleDTO.setName(ROLE_NAME);

    }

    private AddRolesToUserCommand buildCommand(List<String> roles) {
        return new AddRolesToUserCommand(roles, USERNAME);
    }

    @Test
    @DisplayName("should throw NoActionPerformedException if role list is empty")
    void testHandle_whenEmptyRoleList_shouldThrowNoActionPerformed() {
        AddRolesToUserCommand command = buildCommand(Collections.emptyList());

        NoActionPerformedException exception = assertThrows(NoActionPerformedException.class, () ->
                addRolesToUserCommandHandler.handle(command));

        assertEquals(HttpStatus.OK, exception.getStatusCode());
        assertEquals("No action performed because the role list is empty", exception.getBody().getDetail());
    }

    @Test
    @DisplayName("should throw IgrpResponseStatusException if user not found")
    void testHandle_whenUserNotFound_shouldThrowException() {
        AddRolesToUserCommand command = buildCommand(List.of(ROLE_NAME));

        when(userRepository.findByUsername(eq(USERNAME))).thenReturn(Optional.empty());

        IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class, () ->
                addRolesToUserCommandHandler.handle(command));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertNotNull(exception.getBody().getProperties());
        assertEquals("User not found with name: %s".formatted(USERNAME), exception.getBody().getProperties().get("details"));

        verify(userRepository).findByUsername(eq(USERNAME));
        verifyNoInteractions(roleRepository, roleMapper);
    }

    @Test
    @DisplayName("should throw RuntimeException with cause IgrpResponseStatusException if role not found")
    void testHandle_whenRoleNotFound_shouldThrowRuntimeWithCause() {
        AddRolesToUserCommand command = buildCommand(List.of(ROLE_NAME));

        when(userRepository.findByUsername(eq(USERNAME))).thenReturn(Optional.of(user));
        when(roleRepository.findByNameAndStatusNot(eq(ROLE_NAME), eq(Status.DELETED)))
                .thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                addRolesToUserCommandHandler.handle(command));

        assertInstanceOf(IgrpResponseStatusException.class, exception.getCause());

        IgrpResponseStatusException cause = (IgrpResponseStatusException) exception.getCause();
        assertEquals("Role not found with name: %s".formatted(ROLE_NAME),
                cause.getBody().getProperties().get("details"));

        verify(userRepository).findByUsername(eq(USERNAME));
        verify(roleRepository).findByNameAndStatusNot(eq(ROLE_NAME), eq(Status.DELETED));
        verifyNoInteractions(roleMapper);
    }


    @Test
    @DisplayName("should add role to user and return 201 with RoleDTO")
    void testHandle_whenValidCommand_shouldReturnCreatedRoleDTO() {
        AddRolesToUserCommand command = buildCommand(List.of(ROLE_NAME));

        // Arrange
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(roleRepository.findByNameAndStatusNot(eq(ROLE_NAME), eq(Status.DELETED)))
                .thenReturn(Optional.of(role));
        when(roleRepository.findByNameAndStatusNot(ROLE_NAME, Status.DELETED)).thenReturn(Optional.of(role));
        when(roleRepository.save(any(RoleEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
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
        verify(userRepository).findByUsername(USERNAME);
        verify(roleRepository).findByNameAndStatusNot(ROLE_NAME, Status.DELETED);
        verify(roleRepository).save(any(RoleEntity.class));
        verify(roleMapper).mapToDto(role);
        verifyNoMoreInteractions(userRepository, roleRepository, roleMapper);
    }




    @Test
    @DisplayName("should not duplicate user if already present in role")
    void testHandle_whenUserAlreadyInRole_shouldNotDuplicate() {
        // Arrange
        AddRolesToUserCommand command = buildCommand(List.of(ROLE_NAME));

        when(roleRepository.findByNameAndStatusNot(ROLE_NAME,Status.DELETED)).thenReturn(Optional.of(role));
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
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
        verify(userRepository, times(1)).findByUsername(USERNAME);
        verify(roleRepository, times(1)).findByNameAndStatusNot(ROLE_NAME, Status.DELETED);
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

        when(roleRepository.findByNameAndStatusNot(ROLE_NAME, Status.DELETED)).thenReturn(Optional.of(role));
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(roleRepository.save(role)).thenReturn(role);
        when(roleMapper.mapToDto(role)).thenReturn(roleDTO);

        AddRolesToUserCommand command = buildCommand(List.of(ROLE_NAME));

        // Act
        ResponseEntity<List<RoleDTO>> response = addRolesToUserCommandHandler.handle(command);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(1, Objects.requireNonNull(response.getBody()).size());
        assertTrue(role.getUsers().contains(anotherUser));
        assertTrue(role.getUsers().contains(user));

        // Verify
        verify(userRepository).findByUsername(USERNAME);
        verify(roleRepository).findByNameAndStatusNot(ROLE_NAME, Status.DELETED);
        verify(roleMapper).mapToDto(role);
        verify(roleRepository).save(role);
        verifyNoMoreInteractions(userRepository,roleRepository,roleMapper);
    }

    @Test
    @DisplayName("should initialize user set when role has null users")
    void handle_whenRoleUsersIsNull_shouldInitializeSet() {
        AddRolesToUserCommand command = buildCommand(List.of(ROLE_NAME));

        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(roleRepository.findByNameAndStatusNot(ROLE_NAME, Status.DELETED)).thenReturn(Optional.of(role));
        when(roleRepository.save(any(RoleEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        ResponseEntity<List<RoleDTO>> response = addRolesToUserCommandHandler.handle(command);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(role.getUsers());
        assertTrue(role.getUsers().contains(user));

        // Verify
        verify(userRepository).findByUsername(USERNAME);
        verify(roleRepository).findByNameAndStatusNot(ROLE_NAME,Status.DELETED);
        verify(roleRepository).save(role);
        verifyNoMoreInteractions(userRepository,roleRepository);
    }

}
