package cv.igrp.platform.access_management.shared.infrastructure.persistence.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IGRPUserEntity Logic Tests")
class IGRPUserEntityTest {

    private IGRPUserEntity user;
    private RoleEntity activeRole;
    private RoleEntity expiredRole;
    private RoleEntity permanentRole;

    @BeforeEach
    void setUp() {
        user = new IGRPUserEntity();
        
        activeRole = new RoleEntity();
        activeRole.setCode("ACTIVE_ROLE");
        
        expiredRole = new RoleEntity();
        expiredRole.setCode("EXPIRED_ROLE");
        
        permanentRole = new RoleEntity();
        permanentRole.setCode("PERMANENT_ROLE");
    }

    @Test
    @DisplayName("getRoles() should filter out expired roles and include active/permanent ones")
    void getRoles_ShouldFilterExpiredRoles() {
        // Arrange
        UserRoleAssignment activeAssignment = new UserRoleAssignment(user, activeRole, LocalDateTime.now().plusDays(1));
        UserRoleAssignment expiredAssignment = new UserRoleAssignment(user, expiredRole, LocalDateTime.now().minusDays(1));
        UserRoleAssignment permanentAssignment = new UserRoleAssignment(user, permanentRole, null);
        
        user.setUserRoleAssignments(List.of(activeAssignment, expiredAssignment, permanentAssignment));

        // Act
        List<RoleEntity> roles = user.getRoles();

        // Assert
        assertThat(roles).hasSize(2);
        assertThat(roles).extracting(RoleEntity::getCode)
                .containsExactlyInAnyOrder("ACTIVE_ROLE", "PERMANENT_ROLE")
                .doesNotContain("EXPIRED_ROLE");
    }

    @Test
    @DisplayName("getRoles() should return empty list when no assignments exist")
    void getRoles_ShouldReturnEmptyListWhenNoAssignments() {
        // Act
        List<RoleEntity> roles = user.getRoles();

        // Assert
        assertThat(roles).isEmpty();
    }
}
