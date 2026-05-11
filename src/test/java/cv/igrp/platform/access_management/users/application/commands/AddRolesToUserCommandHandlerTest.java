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
import cv.igrp.platform.access_management.users.infrastructure.service.ExpireRoleService;
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

import java.time.LocalDateTime;
import java.util.*;

@ExtendWith(MockitoExtension.class)
public class AddRolesToUserCommandHandlerTest {

    @Mock
    private IGRPUserEntityRepository userRepository;

    @Mock
    private RoleEntityRepository roleRepository;

    @Mock
    private DepartmentEntityRepository departmentRepository;

    @Mock
    private UserRoleAssignmentRepository userRoleAssignmentRepository;

    @Mock
    private SecurityAuditService securityAuditService;

    @Mock
    private ExpireRoleService expireRoleService;

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private AddRolesToUserCommandHandler handler;

    private final Integer USER_ID = 1;
    private IGRPUserEntity user;
    private RoleEntity role1;
    private DepartmentEntity department;

    @BeforeEach
    void setUp() {
        department = new DepartmentEntity();
        department.setCode("DEPT_1");

        role1 = new RoleEntity();
        role1.setId(100);
        role1.setCode("admin");

        user = new IGRPUserEntity();
        user.setId(USER_ID);
    }

    @Test
    @DisplayName("should add roles successfully")
    void testHandle_Success() {
        // Arrange
        AddRolesToUserCommand command = new AddRolesToUserCommand(List.of("admin"), USER_ID, "DEPT_1", LocalDateTime.now().plusDays(1));
        
        lenient().doReturn(Optional.of(user)).when(userRepository).findById(USER_ID);
        lenient().doReturn(department).when(departmentRepository).findByCodeAndStatusNotDeleted("DEPT_1");
        lenient().doReturn(Optional.of(role1)).when(roleRepository).findByDepartmentAndCodeAndStatusNot(any(), eq("admin"), eq(cv.igrp.platform.access_management.shared.application.constants.Status.DELETED));
        RoleDTO roleDto = new RoleDTO(100, "admin", "admin", "admin", null, null, null, null, null, List.of());
        lenient().doReturn(roleDto).when(roleMapper).mapToDto((cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity) any());
        lenient().doReturn(roleDto).when(roleMapper).mapToDto((cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.UserRoleAssignment) any());
        
        // Act
        ResponseEntity<List<RoleDTO>> response = handler.handle(command);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(securityAuditService).logEvent(any(), any(), any());
    }

    @Test
    @DisplayName("should throw exception if user not found")
    void testHandle_UserNotFound() {
        // Arrange
        AddRolesToUserCommand command = new AddRolesToUserCommand(List.of("admin"), USER_ID, "DEPT_1", null);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IgrpResponseStatusException.class, () -> handler.handle(command));
    }
}
