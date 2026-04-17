package cv.igrp.platform.access_management.shared.security;

import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.UserRoleAssignment;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.UserRoleAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("IgrpJwtAuthenticationConverter DB Role Resolution Tests")
class IgrpJwtAuthenticationConverterTest {

    @Mock
    private IGRPUserEntityRepository userRepository;

    @Mock
    private UserRoleAssignmentRepository userRoleAssignmentRepository;

    @InjectMocks
    private IgrpJwtAuthenticationConverter converter;

    private Jwt jwt;
    private IGRPUserEntity user;
    private RoleEntity role;

    @BeforeEach
    void setUp() {
        jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "external-id")
                .claim("name", "Test User")
                .claim("email", "test@example.com")
                .build();

        user = new IGRPUserEntity();
        user.setId(1);
        user.setExternalId("external-id");

        role = new RoleEntity();
        role.setCode("ADMIN");
    }

    @Test
    @DisplayName("should resolve and map active roles from database")
    void convert_ShouldMapRolesFromDatabase() {
        // Arrange
        UserRoleAssignment assignment = new UserRoleAssignment(user, role, null);
        
        when(userRepository.findByExternalId("external-id")).thenReturn(Optional.of(user));
        when(userRoleAssignmentRepository.findActiveByUserId(1)).thenReturn(List.of(assignment));

        // Act
        AbstractAuthenticationToken token = converter.convert(jwt);

        // Assert
        Collection<GrantedAuthority> authorities = token.getAuthorities();
        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_ADMIN");
    }

    @Test
    @DisplayName("should work correctly when user has no roles in database")
    void convert_ShouldWorkWithNoRoles() {
        // Arrange
        when(userRepository.findByExternalId("external-id")).thenReturn(Optional.of(user));
        when(userRoleAssignmentRepository.findActiveByUserId(1)).thenReturn(List.of());

        // Act
        AbstractAuthenticationToken token = converter.convert(jwt);

        // Assert
        assertThat(token.getAuthorities()).isEmpty();
    }

    @Test
    @DisplayName("should work correctly when user is not in database")
    void convert_ShouldWorkWhenUserNotFound() {
        // Arrange
        when(userRepository.findByExternalId("external-id")).thenReturn(Optional.empty());

        // Act
        AbstractAuthenticationToken token = converter.convert(jwt);

        // Assert
        assertThat(token.getAuthorities()).isEmpty();
    }
}
