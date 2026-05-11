package cv.igrp.platform.access_management.users.application.commands;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.UserRoleAssignment;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.UserRoleAssignmentRepository;
import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditService;
import cv.igrp.platform.access_management.shared.domain.events.EventPublisher;
import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
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
public class RemoveRolesFromUserCommandHandlerTest {

    @Mock
    IGRPUserEntityRepository userRepository;

    @Mock
    RoleEntityRepository roleRepository;

    @Mock
    DepartmentEntityRepository departmentRepository;

    @Mock
    UserRoleAssignmentRepository userRoleAssignmentRepository;

    @Mock
    SecurityAuditService securityAuditService;

    @Mock
    RoleMapper roleMapper;

    @Mock
    EventPublisher eventPublisher;

    @InjectMocks
    private RemoveRolesFromUserCommandHandler handler;

    private final Integer USER_ID = 1;
    private IGRPUserEntity user;
    private RoleEntity role1, role2;
    private UserRoleAssignment ura1, ura2;
    private DepartmentEntity department;

    @BeforeEach
    void setUp() {
        department = new DepartmentEntity();
        department.setCode("DEPT_1");

        role1 = new RoleEntity();
        role1.setId(100);
        role1.setCode("admin");

        role2 = new RoleEntity();
        role2.setId(200);
        role2.setCode("user");

        user = new IGRPUserEntity();
        user.setId(USER_ID);

        ura1 = new UserRoleAssignment(user, role1, null);
        ura2 = new UserRoleAssignment(user, role2, null);
    }

    @Test
    @DisplayName("should remove matching role")
    void testHandle_Success() {
        // Arrange
        RemoveRolesFromUserCommand command = new RemoveRolesFromUserCommand(List.of("admin"), USER_ID, "DEPT_1");
        
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRoleAssignmentRepository.findActiveByUserId(USER_ID)).thenReturn(new ArrayList<>(List.of(ura1, ura2)));
        
        // Act
        ResponseEntity<List<RoleDTO>> response = handler.handle(command);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userRoleAssignmentRepository).deleteAll(anyList());
        verify(securityAuditService).logEvent(any(), any(), any());
    }

    @Test
    @DisplayName("should do nothing if role code meta does not match")
    void testHandle_NoMatch() {
        // Arrange
        RemoveRolesFromUserCommand command = new RemoveRolesFromUserCommand(List.of("nonexistent"), USER_ID, "DEPT_1");
        
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRoleAssignmentRepository.findActiveByUserId(USER_ID)).thenReturn(List.of(ura1, ura2));
        when(roleMapper.mapToDto(any(UserRoleAssignment.class))).thenReturn(new RoleDTO(100, "admin", "admin", "admin", null, null, null, null, null, List.of()));
        
        // Act
        ResponseEntity<List<RoleDTO>> response = handler.handle(command);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userRoleAssignmentRepository, never()).deleteAll(anyList());
    }

    @Test
    @DisplayName("should throw exception if user not found")
    void testHandle_UserNotFound() {
        // Arrange
        RemoveRolesFromUserCommand command = new RemoveRolesFromUserCommand(List.of("admin"), USER_ID, "DEPT_1");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IgrpResponseStatusException.class, () -> handler.handle(command));
    }
}