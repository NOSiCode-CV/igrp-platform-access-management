package cv.igrp.platform.access_management.users.application.commands;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.exceptions.NoActionPerformedException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
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
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.util.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AddRolesToUserCommandHandler Tests")
public class AddRolesToUserCommandHandlerTest {

    @Mock IGRPUserEntityRepository userRepository;
    @Mock RoleEntityRepository roleRepository;
    @Mock DepartmentEntityRepository departmentRepository;
    @Mock RoleMapper roleMapper;

    @InjectMocks
    private AddRolesToUserCommandHandler addRolesToUserCommandHandler;

    private IGRPUserEntity user;
    private RoleEntity role;
    private DepartmentEntity department;
    private RoleDTO roleDTO;

    private final Integer ID = 1;
    private final String ROLE_CODE = "admin";
    private final String DEPT_IN_CMD = "DEPT_1";

    @BeforeEach
    void setUp() {
        user = new IGRPUserEntity();
        user.setId(ID);
        user.setRoles(new ArrayList<>());

        department = new DepartmentEntity();
        department.setCode(DEPT_IN_CMD);

        role = new RoleEntity();
        role.setCode(ROLE_CODE);
        role.setDepartment(department);
        role.setUsers(null);

        roleDTO = new RoleDTO();
        roleDTO.setCode(ROLE_CODE);
    }

    private AddRolesToUserCommand buildCommand(List<String> roles) {
        return new AddRolesToUserCommand(roles, ID, DEPT_IN_CMD);
    }

    @Test
    @DisplayName("should throw NoActionPerformedException if role list is empty")
    void testHandle_whenEmptyRoleList_shouldThrowNoActionPerformed() {
        AddRolesToUserCommand command = buildCommand(Collections.emptyList());

        NoActionPerformedException exception = assertThrows(
                NoActionPerformedException.class,
                () -> addRolesToUserCommandHandler.handle(command)
        );

        assertEquals(HttpStatus.OK, exception.getStatusCode());
        assertEquals("No action performed because the role list is empty", exception.getBody().getDetail());
    }

    @Test
    @DisplayName("should throw IgrpResponseStatusException if user not found")
    void testHandle_whenUserNotFound_shouldThrowException() {
        AddRolesToUserCommand command = buildCommand(List.of(ROLE_CODE));

        when(departmentRepository.findByCodeAndStatusNotDeleted(DEPT_IN_CMD)).thenReturn(department);
        when(userRepository.findById(ID)).thenReturn(Optional.empty());

        IgrpResponseStatusException ex = assertThrows(
                IgrpResponseStatusException.class,
                () -> addRolesToUserCommandHandler.handle(command)
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("User not found with ID: %s".formatted(ID),
                ex.getBody().getProperties().get("details"));
    }

    @Test
    @DisplayName("should throw IgrpResponseStatusException if role not found")
    void testHandle_whenRoleNotFound_shouldThrowRuntimeWithCause() {
        AddRolesToUserCommand command = buildCommand(List.of(ROLE_CODE));

        when(departmentRepository.findByCodeAndStatusNotDeleted(DEPT_IN_CMD)).thenReturn(department);
        when(userRepository.findById(ID)).thenReturn(Optional.of(user));
        when(roleRepository.findByDepartmentAndCodeAndStatusNot(department, ROLE_CODE, Status.DELETED))
                .thenReturn(Optional.empty());

        IgrpResponseStatusException ex = assertThrows(
                IgrpResponseStatusException.class,
                () -> addRolesToUserCommandHandler.handle(command)
        );

        assertTrue(ex.getBody().getProperties().get("details").toString()
                .contains("Role not found with code: %s".formatted(ROLE_CODE)));
    }

    @Test
    @DisplayName("should add role to user and return 201 with RoleDTO")
    void testHandle_whenValidCommand_shouldReturnCreatedRoleDTO() {
        AddRolesToUserCommand command = buildCommand(List.of(ROLE_CODE));

        when(departmentRepository.findByCodeAndStatusNotDeleted(DEPT_IN_CMD))
                .thenReturn(department);
        when(userRepository.findById(ID)).thenReturn(Optional.of(user));
        when(roleRepository.findByDepartmentAndCodeAndStatusNot(department, ROLE_CODE, Status.DELETED))
                .thenReturn(Optional.of(role));
        when(roleRepository.save(any(RoleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(roleMapper.mapToDto(role)).thenReturn(roleDTO);

        ResponseEntity<List<RoleDTO>> response = addRolesToUserCommandHandler.handle(command);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals(roleDTO, response.getBody().getFirst());
        assertTrue(role.getUsers().contains(user));
    }

    @Test
    @DisplayName("should not duplicate user if already present in role")
    void testHandle_whenUserAlreadyInRole_shouldNotDuplicate() {
        AddRolesToUserCommand command = buildCommand(List.of(ROLE_CODE));

        role.setUsers(new HashSet<>(List.of(user)));

        when(departmentRepository.findByCodeAndStatusNotDeleted(DEPT_IN_CMD))
                .thenReturn(department);
        when(userRepository.findById(ID)).thenReturn(Optional.of(user));
        when(roleRepository.findByDepartmentAndCodeAndStatusNot(department, ROLE_CODE, Status.DELETED))
                .thenReturn(Optional.of(role));
        when(roleRepository.save(role)).thenReturn(role);
        when(roleMapper.mapToDto(role)).thenReturn(roleDTO);

        ResponseEntity<List<RoleDTO>> response = addRolesToUserCommandHandler.handle(command);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    @DisplayName("should add user to existing users in role")
    void testHandle_whenRoleHasOtherUsers_shouldAddNewUser() {
        IGRPUserEntity anotherUser = new IGRPUserEntity();
        anotherUser.setId(50);
        role.setUsers(new HashSet<>(List.of(anotherUser)));

        AddRolesToUserCommand command = buildCommand(List.of(ROLE_CODE));

        when(departmentRepository.findByCodeAndStatusNotDeleted(DEPT_IN_CMD))
                .thenReturn(department);
        when(userRepository.findById(ID)).thenReturn(Optional.of(user));
        when(roleRepository.findByDepartmentAndCodeAndStatusNot(department, ROLE_CODE, Status.DELETED))
                .thenReturn(Optional.of(role));
        when(roleRepository.save(role)).thenReturn(role);
        when(roleMapper.mapToDto(role)).thenReturn(roleDTO);

        ResponseEntity<List<RoleDTO>> response = addRolesToUserCommandHandler.handle(command);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertTrue(role.getUsers().contains(anotherUser));
        assertTrue(role.getUsers().contains(user));
    }

    @Test
    @DisplayName("should initialize user set when role has null users")
    void handle_whenRoleUsersIsNull_shouldInitializeSet() {
        AddRolesToUserCommand command = buildCommand(List.of(ROLE_CODE));

        role.setUsers(null);

        when(departmentRepository.findByCodeAndStatusNotDeleted(DEPT_IN_CMD))
                .thenReturn(department);
        when(userRepository.findById(ID)).thenReturn(Optional.of(user));
        when(roleRepository.findByDepartmentAndCodeAndStatusNot(department, ROLE_CODE, Status.DELETED))
                .thenReturn(Optional.of(role));
        when(roleRepository.save(any(RoleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<List<RoleDTO>> response = addRolesToUserCommandHandler.handle(command);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(role.getUsers());
        assertTrue(role.getUsers().contains(user));
    }
}
