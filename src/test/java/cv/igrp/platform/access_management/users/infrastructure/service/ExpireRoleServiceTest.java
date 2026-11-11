package cv.igrp.platform.access_management.users.infrastructure.service;

import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditService;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditCategory;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditEventType;
import cv.igrp.platform.access_management.shared.domain.events.EventPublisher;
import cv.igrp.platform.access_management.shared.domain.events.UserRoleChangedEvent;
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
import java.util.Set;

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

    @Mock
    private EventPublisher eventPublisher;

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
    @DisplayName("removeExpiredRoles must publish UserRoleChangedEvent so the permission cache is evicted")
    void removeExpiredRoles_ShouldPublishUserRoleChangedEvent() {
        // Without this event nothing reaches PermissionSetEviction, and the
        // user's permission sets would keep the expired role's keys until
        // their TTL runs out.
        when(userRoleAssignmentRepository.findExpiredRoles()).thenReturn(List.of(assignment));

        expireRoleService.removeExpiredRoles();

        ArgumentCaptor<UserRoleChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(UserRoleChangedEvent.class);
        verify(eventPublisher).publishUserRoleChanged(eventCaptor.capture());

        UserRoleChangedEvent event = eventCaptor.getValue();
        // The userId must be the value the cache keys are built from — the
        // internal UUID that travels as the JWT subject. Publishing any other
        // identifier would make evictBySubject a silent no-op, which is the
        // exact failure this test exists to prevent.
        assertThat(event.getUserId()).isEqualTo("00000000-0000-0000-0000-000000000001");
        assertThat(event.getAffectedRoleCodes()).containsExactly("TEST_ROLE");
        assertThat(event.getChangeType()).isEqualTo(UserRoleChangedEvent.CHANGE_EXPIRED);
        assertThat(event.getTriggeredBy()).isEqualTo("SYSTEM");
    }

    @Test
    @DisplayName("removeExpiredRoles publishes one event per user, not per assignment")
    void removeExpiredRoles_ShouldGroupEventsByUser() {
        // Eviction is keyed by subject, so three roles expiring for one user is
        // one eviction. A second user still gets their own event.
        RoleEntity secondRole = new RoleEntity();
        secondRole.setCode("SECOND_ROLE");
        UserRoleAssignment sameUserOtherRole =
                new UserRoleAssignment(user, secondRole, LocalDateTime.now().minusMinutes(1));

        IGRPUserEntity otherUser = new IGRPUserEntity();
        otherUser.setId("00000000-0000-0000-0000-000000000002");
        UserRoleAssignment otherUserAssignment =
                new UserRoleAssignment(otherUser, role, LocalDateTime.now().minusMinutes(1));

        when(userRoleAssignmentRepository.findExpiredRoles())
                .thenReturn(List.of(assignment, sameUserOtherRole, otherUserAssignment));

        expireRoleService.removeExpiredRoles();

        ArgumentCaptor<UserRoleChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(UserRoleChangedEvent.class);
        verify(eventPublisher, times(2)).publishUserRoleChanged(eventCaptor.capture());

        Map<String, Set<String>> byUser = eventCaptor.getAllValues().stream()
                .collect(java.util.stream.Collectors.toMap(
                        UserRoleChangedEvent::getUserId,
                        UserRoleChangedEvent::getAffectedRoleCodes));

        assertThat(byUser).hasSize(2);
        assertThat(byUser.get("00000000-0000-0000-0000-000000000001"))
                .containsExactlyInAnyOrder("TEST_ROLE", "SECOND_ROLE");
        assertThat(byUser.get("00000000-0000-0000-0000-000000000002"))
                .containsExactly("TEST_ROLE");
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
        verify(eventPublisher, never()).publishUserRoleChanged(any());
    }
}
