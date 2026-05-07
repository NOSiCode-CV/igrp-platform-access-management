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
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("IgrpJwtAuthenticationConverter Tests")
class IgrpJwtAuthenticationConverterTest {

    @Mock
    private IGRPUserEntityRepository userRepository;

    @Mock
    private UserRoleAssignmentRepository userRoleAssignmentRepository;

    @InjectMocks
    private IgrpJwtAuthenticationConverter converter;

    private IGRPUserEntity user;
    private RoleEntity role;

    private Jwt createMockJwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claims(c -> c.putAll(claims))
                .build();
    }

    @BeforeEach
    void setUp() {
        user = new IGRPUserEntity();
        user.setId(1);
        user.setExternalId("external-id");

        role = new RoleEntity();
        role.setCode("ADMIN");
    }

    // --- DB Role Resolution Tests (from feature/temporary-profile) ---

    @Test
    @DisplayName("should resolve and map active roles from database")
    void convert_ShouldMapRolesFromDatabase() {
        Jwt jwt = createMockJwt(Map.of("sub", "external-id", "email", "test@example.com"));
        UserRoleAssignment assignment = new UserRoleAssignment(user, role, null);

        when(userRepository.findByExternalId("external-id")).thenReturn(Optional.of(user));
        when(userRoleAssignmentRepository.findActiveByUserId(1)).thenReturn(List.of(assignment));

        AbstractAuthenticationToken token = converter.convert(jwt);

        Collection<GrantedAuthority> authorities = token.getAuthorities();
        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_ADMIN");
    }

    @Test
    @DisplayName("should work correctly when user has no roles in database")
    void convert_ShouldWorkWithNoRoles() {
        Jwt jwt = createMockJwt(Map.of("sub", "external-id", "email", "test@example.com"));

        when(userRepository.findByExternalId("external-id")).thenReturn(Optional.of(user));
        when(userRoleAssignmentRepository.findActiveByUserId(1)).thenReturn(List.of());

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(token.getAuthorities()).isEmpty();
    }

    @Test
    @DisplayName("should work correctly when user is not in database")
    void convert_ShouldWorkWhenUserNotFound() {
        Jwt jwt = createMockJwt(Map.of("sub", "external-id", "email", "test@example.com"));

        when(userRepository.findByExternalId("external-id")).thenReturn(Optional.empty());

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(token.getAuthorities()).isEmpty();
    }

    // --- Profile & Claims Normalization Tests (from version/0.2.0-beta) ---

    @Test
    void testConvertValidPwDJwt() {
        Map<String, Object> claims = Map.of(
                "sub", "user-123",
                "iss", "https://mock-issuer.com",
                "email", "Test@EXAMPle.com",
                "given_name", "John",
                "family_name", "Doe",
                "acr", "pwd"
        );

        when(userRepository.findByExternalId("user-123")).thenReturn(Optional.empty());

        Jwt jwt = createMockJwt(claims);
        var authObj = converter.convert(jwt);

        assertNotNull(authObj);
        assertTrue(authObj instanceof OidcContextAuthenticationToken);

        OidcContextAuthenticationToken token = (OidcContextAuthenticationToken) authObj;
        IgrpOidcUser oidcUser = (IgrpOidcUser) token.getPrincipal();
        UserProfile profile = oidcUser.getUserProfile();

        assertEquals("user-123", profile.id());
        assertEquals("https://mock-issuer.com", profile.issuer());
        assertEquals("test@example.com", profile.email());
        assertEquals("pwd", profile.authMethod());
        assertEquals("John Doe", profile.fullName());
    }

    @Test
    void testConvertCniUsesSubAsNicFallbackIfNicMissing() {
        Map<String, Object> claims = Map.of(
                "sub", "user-cni",
                "acr", "cni",
                "email", "test@example.com"
        );

        when(userRepository.findByExternalId("user-cni")).thenReturn(Optional.empty());

        Jwt jwt = createMockJwt(claims);
        AbstractAuthenticationToken authObj = converter.convert(jwt);

        OidcContextAuthenticationToken token = (OidcContextAuthenticationToken) authObj;
        UserProfile profile = token.getPrincipal().getUserProfile();
        assertEquals("USERCNI", profile.nic());
    }

    @Test
    void testConvertCniPassesIfNicPresent() {
        Map<String, Object> claims = Map.of(
                "sub", "user-cni",
                "acr", "cni",
                "national_id", " 1 2 3 4 5 6 7 8 M ",
                "email", "test@example.com"
        );

        when(userRepository.findByExternalId("user-cni")).thenReturn(Optional.empty());

        Jwt jwt = createMockJwt(claims);
        var authObj = converter.convert(jwt);

        assertNotNull(authObj);
        OidcContextAuthenticationToken token = (OidcContextAuthenticationToken) authObj;
        UserProfile profile = ((IgrpOidcUser) token.getPrincipal()).getUserProfile();

        assertEquals("cni", profile.authMethod());
        assertEquals("12345678M", profile.nic());
        assertEquals("user-cni", profile.id());
    }

    @Test
    void testConvertMissingRequiredSub() {
        Map<String, Object> claims = Map.of(
                "email", "test@example.com"
        );

        Jwt jwt = createMockJwt(claims);

        OAuth2AuthenticationException exception = assertThrows(OAuth2AuthenticationException.class, () -> {
            converter.convert(jwt);
        });

        assertEquals("missing_claim", exception.getError().getErrorCode());
        assertTrue(exception.getMessage().contains("sub"));
    }

    @Test
    void testNormalizationEdgeCases() {
        Map<String, Object> claims = Map.of(
                "sub", "user",
                "email", " invalid-email ",
                "phone_number", " +238 999 99 99 foo",
                "nic", "   "
        );

        when(userRepository.findByExternalId("user")).thenReturn(Optional.empty());

        Jwt jwt = createMockJwt(claims);
        var authObj = converter.convert(jwt);

        UserProfile profile = ((IgrpOidcUser) ((OidcContextAuthenticationToken) authObj).getPrincipal()).getUserProfile();

        assertEquals("", profile.email());
        assertEquals("+2389999999", profile.phone());
        assertEquals("", profile.nic());
        assertEquals("user", profile.id());
    }
}
