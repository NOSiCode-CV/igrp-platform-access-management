package cv.igrp.platform.access_management.users.infrastructure.service;

import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditService;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditCategory;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditEventType;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.UserRoleAssignment;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.UserRoleAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExpireRoleService Tests")
class ExpireRoleServiceTest {

    @Mock
    private UserRoleAssignmentRepository userRoleAssignmentRepository;

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private SecurityAuditService securityAuditService;

    @InjectMocks
    private ExpireRoleService expireRoleService;

    private UserRoleAssignment assignment;
    private IGRPUserEntity user;
    private RoleEntity role;

    @BeforeEach
    void setUp() {
        user = new IGRPUserEntity();
        user.setId("00000000-0000-0000-0000-000000000001");
        
        role = new RoleEntity();
        role.setCode("TEST_ROLE");
        
        assignment = new UserRoleAssignment(user, role, LocalDateTime.now().plusHours(1));
    }

    @Test
    @DisplayName("should schedule expiration task when expiresAt is not null")
    void scheduleExpiration_ShouldScheduleTask() {
        // Act
        expireRoleService.scheduleExpiration(assignment);

        // Assert
        verify(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    @DisplayName("should not schedule expiration task when expiresAt is null")
    void scheduleExpiration_ShouldNotScheduleTask_WhenNoExpiration() {
        // Arrange
        assignment.setExpiresAt(null);

        // Act
        expireRoleService.scheduleExpiration(assignment);

        // Assert
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    @DisplayName("removeExpiredRoles should find, delete roles and log audit event")
    void removeExpiredRoles_ShouldCleanupAndLog() {
        // Arrange
        when(userRoleAssignmentRepository.findExpiredRoles()).thenReturn(List.of(assignment));

        // Act
        expireRoleService.removeExpiredRoles();

        // Assert
        verify(userRoleAssignmentRepository).deleteAll(anyList());
        
        ArgumentCaptor<Map<String, Object>> contextCaptor = ArgumentCaptor.forClass(Map.class);
        verify(securityAuditService).logEvent(
                eq(AuditEventType.ROLE_EXPIRED),
                eq(AuditCategory.PRIVILEGE),
                contextCaptor.capture()
        );
        
        Map<String, Object> auditContext = contextCaptor.getValue();
        assertThat(auditContext.get("userId")).isEqualTo("00000000-0000-0000-0000-000000000001");
        assertThat(auditContext.get("roleCode")).isEqualTo("TEST_ROLE");
    }

    @Test
    @DisplayName("removeExpiredRoles should do nothing when no expired roles found")
    void removeExpiredRoles_ShouldDoNothingWhenNoneFound() {
        // Arrange
        when(userRoleAssignmentRepository.findExpiredRoles()).thenReturn(Collections.emptyList());

        // Act
        expireRoleService.removeExpiredRoles();

        // Assert
        verify(userRoleAssignmentRepository, never()).deleteAll(anyList());
        verify(securityAuditService, never()).logEvent(any(), any(), any());
    }
}
