package cv.igrp.platform.access_management.shared.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IgrpJwtAuthenticationConverter.class)
class IgrpJwtAuthenticationConverterTest {

    @Autowired
    private IgrpJwtAuthenticationConverter converter;

    private Jwt createMockJwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claims(c -> c.putAll(claims))
                .build();
    }

    @Test
    void testConvertValidPwDJwt() {
        Map<String, Object> claims = Map.of(
                "sub", "user-123",
                "iss", "https://mock-issuer.com",
                "email", "Test@EXAMPle.com",
                "given_name", "John",
                "family_name", "Doe",
                "roles", List.of("admin", "user"),
                "acr", "pwd"
        );

        Jwt jwt = createMockJwt(claims);
        var authObj = converter.convert(jwt);

        assertNotNull(authObj);
        assertTrue(authObj instanceof OidcContextAuthenticationToken);
        
        OidcContextAuthenticationToken token = (OidcContextAuthenticationToken) authObj;
        IgrpOidcUser oidcUser = (IgrpOidcUser) token.getPrincipal();
        UserProfile profile = oidcUser.getUserProfile();

        assertEquals("user-123", profile.externalId());
        assertEquals("https://mock-issuer.com", profile.issuer());
        assertEquals("test@example.com", profile.email());
        assertEquals("pwd", profile.authMethod());
        assertEquals("John Doe", profile.fullName());
        
        Collection<? extends GrantedAuthority> authorities = oidcUser.getAuthorities();
        assertTrue(authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_admin")));
        assertTrue(authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_user")));
    }

    @Test
    void testConvertFallbackToGroups() {
        Map<String, Object> claims = Map.of(
                "sub", "user-123",
                "groups", List.of("manager"),
                "email", "test@example.com"
        );

        Jwt jwt = createMockJwt(claims);
        var authObj = converter.convert(jwt);

        assertNotNull(authObj);
        Collection<? extends GrantedAuthority> authorities = authObj.getAuthorities();
        assertTrue(authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_manager")));
        assertFalse(authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_groups")));
    }

    @Test
    void testConvertCniThrowsExceptionIfNicMissing() {
        Map<String, Object> claims = Map.of(
                "sub", "user-cni",
                "acr", "cni",
                "email", "test@example.com"
        );

        Jwt jwt = createMockJwt(claims);

        OAuth2AuthenticationException exception = assertThrows(OAuth2AuthenticationException.class, () -> {
            converter.convert(jwt);
        });

        assertEquals("missing_nic", exception.getError().getErrorCode());
        assertTrue(exception.getMessage().contains("Login via CNI requires the Civil Identification Number"));
    }

    @Test
    void testConvertCniPassesIfNicPresent() {
        Map<String, Object> claims = Map.of(
                "sub", "user-cni",
                "acr", "cni",
                "national_id", " 1 2 3 4 5 6 7 8 M ",
                "email", "test@example.com"
        );

        Jwt jwt = createMockJwt(claims);
        var authObj = converter.convert(jwt);

        assertNotNull(authObj);
        OidcContextAuthenticationToken token = (OidcContextAuthenticationToken) authObj;
        UserProfile profile = ((IgrpOidcUser) token.getPrincipal()).getUserProfile();

        assertEquals("cni", profile.authMethod());
        assertEquals("12345678M", profile.nic()); // nic extraction normalized
        assertEquals("user-cni", profile.externalId());
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
        
        Jwt jwt = createMockJwt(claims);
        var authObj = converter.convert(jwt);
        
        UserProfile profile = ((IgrpOidcUser) ((OidcContextAuthenticationToken) authObj).getPrincipal()).getUserProfile();
        
        // Email match fails Regex, returns null string or ""? nullSafe returns "" (empty string)
        assertEquals("", profile.email());
        assertEquals("+2389999999", profile.phone());
        assertEquals("", profile.nic());
        assertEquals("user", profile.externalId());
    }
}
