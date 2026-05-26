package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditService;
import cv.igrp.platform.access_management.shared.domain.audit.AuthAuditContext;
import cv.igrp.platform.access_management.shared.domain.audit.IdentifierType;
import cv.igrp.platform.access_management.shared.infrastructure.service.AuthAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtTokenConfigTest {

    @Mock private ClaimsEnrichmentService claimsService;
    @Mock private AuthAuditService authAuditService;
    @Mock private SecurityAuditService auditService;
    @Mock private SessionIssuanceService sessionIssuanceService;
    @Mock private JwtEncodingContext context;

    private OAuth2TokenCustomizer<JwtEncodingContext> customizer;
    private JwtClaimsSet.Builder claims;

    @BeforeEach
    void setUp() {
        JwtTokenConfig config = new JwtTokenConfig(null,
                "http://localhost:8090",
                "test-key",
                "",
                "",
                "");
        customizer = config.igrpTokenCustomizer(claimsService, authAuditService, auditService, sessionIssuanceService);
        claims = JwtClaimsSet.builder().id("jti-1");
    }

    @Test
    void oauthPrincipalUsesEmailMappingBeforeSubjectMapping() {
        stubOAuthContext(Map.of(
                "sub", "external-sub",
                "email", "demo@nosi.cv"
        ));
        when(claimsService.mapEmail("autentika", "demo@nosi.cv")).thenReturn("email-user");
        when(claimsService.buildClaims("email-user", "client-a", Set.of("openid"))).thenReturn(Map.of());
        when(claimsService.buildTokenIssuedAuditContext("email-user", "client-a", null))
                .thenReturn(auditContext("email-user"));

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
        when(claimsService.buildTokenIssuedAuditContext("subject-user", "client-a", null))
                .thenReturn(auditContext("subject-user"));

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
        RegisteredClient registeredClient = RegisteredClient.withId("registered-client-id")
                .clientId("client-a")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://client.example/callback")
                .build();

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getPrincipal()).thenReturn(principal);
        when(context.getRegisteredClient()).thenReturn(registeredClient);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        when(context.getClaims()).thenReturn(claims);
        when(context.getAuthorizedScopes()).thenReturn(Set.of("openid"));
    }

    private AuthAuditContext auditContext(String userId) {
        return new AuthAuditContext(
                IdentifierType.UNKNOWN,
                null,
                userId,
                "client-a",
                null,
                null
        );
    }
}
