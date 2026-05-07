package cv.igrp.platform.access_management.users.application.queries;

import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.UserRoleAssignment;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetCurrentUserRolesQueryHandler Tests")
class GetCurrentUserRolesQueryHandlerTest {

    @Mock
    private IGRPUserEntityRepository userRepository;

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private AuthenticationHelper authenticationHelper;

    @InjectMocks
    private GetCurrentUserRolesQueryHandler handler;

    private IGRPUserEntity user;
    private RoleEntity activeRole;
    private RoleEntity expiredRole;

    @BeforeEach
    void setUp() {
        user = new IGRPUserEntity();
        user.setId(1);
        
        activeRole = new RoleEntity();
        activeRole.setCode("ACTIVE");
        activeRole.setStatus(Status.ACTIVE);
        
        expiredRole = new RoleEntity();
        expiredRole.setCode("EXPIRED");
        expiredRole.setStatus(Status.ACTIVE);
    }

    @Test
    @DisplayName("should filter expired roles and return active ones with expiresAt")
    void handle_ShouldFilterExpiredRoles() {
        // Arrange
        LocalDateTime future = LocalDateTime.now().plusDays(1);
        LocalDateTime past = LocalDateTime.now().minusDays(1);
        
        UserRoleAssignment activeAssignment = new UserRoleAssignment(user, activeRole, future);
        UserRoleAssignment expiredAssignment = new UserRoleAssignment(user, expiredRole, past);
        
        user.setUserRoleAssignments(List.of(activeAssignment, expiredAssignment));
        
        when(authenticationHelper.getSub()).thenReturn("1");
        when(userRepository.findByIdWithRolesAndPermissions(1)).thenReturn(Optional.of(user));
        
        RoleDTO dto = new RoleDTO();
        dto.setCode("ACTIVE");
        dto.setExpiresAt(future);
        when(roleMapper.mapToDto(any(UserRoleAssignment.class))).thenReturn(dto);

        // Act
        ResponseEntity<List<RoleDTO>> response = handler.handle(new GetCurrentUserRolesQuery());

        // Assert
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getCode()).isEqualTo("ACTIVE");
        assertThat(response.getBody().get(0).getExpiresAt()).isEqualTo(future);
    }

    @Test
    @DisplayName("should return empty list when user has no roles")
    void handle_ShouldReturnEmptyListWhenNoRoles() {
        // Arrange
        user.setUserRoleAssignments(Collections.emptyList());
        when(authenticationHelper.getSub()).thenReturn("1");
        when(userRepository.findByIdWithRolesAndPermissions(1)).thenReturn(Optional.of(user));

        // Act
        ResponseEntity<List<RoleDTO>> response = handler.handle(new GetCurrentUserRolesQuery());

        // Assert
        assertThat(response.getBody()).isEmpty();
    }
}