package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtTokenConfigTest {

    @Mock private ClaimsEnrichmentService claimsService;
    @Mock private SecurityAuditService auditService;
    @Mock private SessionIssuanceService sessionIssuanceService;
    @Mock private JwtEncodingContext context;

    private OAuth2TokenCustomizer<JwtEncodingContext> customizer;
    private JwtClaimsSet.Builder claims;

    @BeforeEach
    void setUp() {
        config = new JwtTokenConfig(null,
                "http://localhost:8090",
                "test-key",
                "",
                "",
                "");
        customizer = config.igrpTokenCustomizer(claimsService, auditService, sessionIssuanceService,
                300, 28800);
        claims = JwtClaimsSet.builder().id("jti-1").issuedAt(ISSUED_AT);
    }

    private static final Instant ISSUED_AT = Instant.parse("2026-11-15T10:00:00Z");
    private JwtTokenConfig config;

    @Test
    @DisplayName("#33: a user's access token says which client it was issued to and lives the configured time")
    void userAccessTokenLifetimeAndAzp() {
        stubOAuthContext(Map.of("sub", "external-sub", "email", "demo@nosi.cv"));
        when(claimsService.mapEmail("autentika", "demo@nosi.cv")).thenReturn("email-user");
        when(claimsService.buildClaims("email-user", "client-a", Set.of("openid"))).thenReturn(Map.of());
        // The client allows 5 minutes (its default); the platform says 2.
        config.igrpTokenCustomizer(claimsService, auditService, sessionIssuanceService,
                120, 28800).customize(context);

        JwtClaimsSet built = claims.build();
        assertEquals("client-a", built.getClaimAsString("azp"));
        assertEquals(ISSUED_AT.plus(Duration.ofMinutes(2)), built.getExpiresAt());
    }

    @Test
    @DisplayName("#33: a client with a shorter access token lifetime keeps it")
    void shorterClientLifetimeWins() {
        stubOAuthContext(Map.of("sub", "external-sub", "email", "demo@nosi.cv"));
        when(claimsService.mapEmail("autentika", "demo@nosi.cv")).thenReturn("email-user");
        when(claimsService.buildClaims("email-user", "client-a", Set.of("openid"))).thenReturn(Map.of());
        config.igrpTokenCustomizer(claimsService, auditService, sessionIssuanceService,
                1800, 28800).customize(context);

        assertEquals(ISSUED_AT.plus(Duration.ofMinutes(5)), claims.build().getExpiresAt());
    }

    @Test
    @DisplayName("#33: a refresh after the refresh token's maximum lifetime is refused; before it, it works")
    void refreshMaximumLifetime() {
        stubOAuthContext(Map.of("sub", "external-sub", "email", "demo@nosi.cv"));
        when(claimsService.mapEmail("autentika", "demo@nosi.cv")).thenReturn("email-user");
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.REFRESH_TOKEN);

        when(context.getAuthorization()).thenReturn(signedIn(Duration.ofHours(9)));
        OAuth2AuthenticationException late = assertThrows(OAuth2AuthenticationException.class,
                () -> customizer.customize(context));
        assertEquals(OAuth2ErrorCodes.INVALID_GRANT, late.getError().getErrorCode());

        // No sign-in to count from (a grant without a code): refused, not unbounded.
        when(context.getAuthorization()).thenReturn(OAuth2Authorization.withRegisteredClient(client())
                .principalName("fulano").authorizationGrantType(AuthorizationGrantType.DEVICE_CODE).build());
        assertThrows(OAuth2AuthenticationException.class, () -> customizer.customize(context));

        when(context.getAuthorization()).thenReturn(signedIn(Duration.ofHours(1)));
        when(claimsService.buildClaims("email-user", "client-a", Set.of("openid"))).thenReturn(Map.of());
        assertDoesNotThrow(() -> customizer.customize(context));
    }

    @Test
    @DisplayName("#33: the ID token carries the user's profile; Spring's OIDC sid stays for logout")
    void idTokenProfileKeepsOidcSid() {
        RegisteredClient client = client();
        OAuth2AccessToken access = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "at",
                ISSUED_AT, ISSUED_AT.plusSeconds(300));
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
                .principalName("fulano").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .token(access, metadata -> metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME,
                        Map.of("sub", "u-1", "device_id", "device-1")))
                .build();
        when(context.getTokenType()).thenReturn(new OAuth2TokenType("id_token"));
        when(context.getAuthorization()).thenReturn(authorization);
        when(context.getAuthorizedScopes()).thenReturn(Set.of("openid", "profile"));
        when(context.getClaims()).thenReturn(claims.claim("sid", "spring-session"));
        when(claimsService.buildIdTokenClaims("u-1", Set.of("openid", "profile")))
                .thenReturn(Map.of("preferred_username", "fulano", "is_super_admin", false));

        customizer.customize(context);

        JwtClaimsSet built = claims.build();
        assertEquals("spring-session", built.getClaimAsString("sid"));
        assertEquals("fulano", built.getClaimAsString("preferred_username"));
        assertEquals("device-1", built.getClaimAsString("device_id"));
    }

    private OAuth2Authorization signedIn(Duration ago) {
        Instant at = Instant.now().minus(ago);
        return OAuth2Authorization.withRegisteredClient(client()).principalName("fulano")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .token(new OAuth2AuthorizationCode("code", at, at.plusSeconds(300)))
                .build();
    }

    private static RegisteredClient client() {
        return RegisteredClient.withId("registered-client-id")
                .clientId("client-a")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://client.example/callback")
                .build();
    }

    @Test
    void oauthPrincipalUsesEmailMappingBeforeSubjectMapping() {
        stubOAuthContext(Map.of(
                "sub", "external-sub",
                "email", "demo@nosi.cv"
        ));
        when(claimsService.mapEmail("autentika", "demo@nosi.cv")).thenReturn("email-user");
        when(claimsService.buildClaims("email-user", "client-a", Set.of("openid"))).thenReturn(Map.of());

        customizer.customize(context);

        assertEquals("email-user", claims.build().getSubject());
        verify(claimsService).mapEmail("autentika", "demo@nosi.cv");
        verify(claimsService, never()).mapSubject(any(), any());
    }

    @Test
    void oauthPrincipalFallsBackToSubjectMappingWhenEmailDoesNotResolve() {
        stubOAuthContext(Map.of(
                "sub", "external-sub",
                "email", "missing@nosi.cv"
        ));
        when(claimsService.mapEmail("autentika", "missing@nosi.cv")).thenReturn(null);
        when(claimsService.mapSubject("autentika", "external-sub")).thenReturn("subject-user");
        when(claimsService.buildClaims("subject-user", "client-a", Set.of("openid"))).thenReturn(Map.of());

        customizer.customize(context);

        assertEquals("subject-user", claims.build().getSubject());
        verify(claimsService).mapEmail("autentika", "missing@nosi.cv");
        verify(claimsService).mapSubject("autentika", "external-sub");
    }

    private void stubOAuthContext(Map<String, Object> attributes) {
        DefaultOAuth2User oauth2User = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                attributes,
                "sub"
        );
        OAuth2AuthenticationToken principal = new OAuth2AuthenticationToken(
                oauth2User,
                oauth2User.getAuthorities(),
                "autentika"
        );
        RegisteredClient registeredClient = client();

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getPrincipal()).thenReturn(principal);
        when(context.getRegisteredClient()).thenReturn(registeredClient);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        when(context.getClaims()).thenReturn(claims);
        when(context.getAuthorizedScopes()).thenReturn(Set.of("openid"));
    }
}
