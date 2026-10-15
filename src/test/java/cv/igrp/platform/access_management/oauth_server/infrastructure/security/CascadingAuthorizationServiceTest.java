package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Two corrections reported by the frontend team (findings 4 and 5).
 *
 * <p>Before these, the behaviour was exactly backwards: revoking your CURRENT
 * refresh token left the session alive, while revoking an already-rotated one
 * destroyed it.
 */
class CascadingAuthorizationServiceTest {

    private static final String TOKEN_ENDPOINT = "/oauth2/token";

    private OAuth2AuthorizationService delegate;
    private RevocationCascadeListener cascadeListener;
    private RefreshTokenReuseGuard reuseGuard;
    private CascadingAuthorizationService service;

    @BeforeEach
    void setUp() {
        delegate = mock(OAuth2AuthorizationService.class);
        cascadeListener = mock(RevocationCascadeListener.class);
        reuseGuard = mock(RefreshTokenReuseGuard.class);
        service = new CascadingAuthorizationService(delegate, cascadeListener, reuseGuard, TOKEN_ENDPOINT);
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void servingRequestTo(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRequestURI(uri);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private RegisteredClient client() {
        return RegisteredClient.withId("1")
                .clientId("igrp-access-management")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://app.example/callback")
                .tokenSettings(TokenSettings.builder().accessTokenTimeToLive(Duration.ofHours(1)).build())
                .build();
    }

    private OAuth2Authorization authorization(String refreshValue, boolean refreshInvalidated) {
        Instant now = Instant.now();
        OAuth2Authorization.Builder builder = OAuth2Authorization.withRegisteredClient(client())
                .id("auth-1")
                .principalName("user")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .accessToken(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                        "access-value", now, now.plusSeconds(3600)));
        OAuth2RefreshToken refresh = new OAuth2RefreshToken(refreshValue, now, now.plusSeconds(86400));
        if (refreshInvalidated) {
            builder.token(refresh, metadata -> metadata.put(
                    OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, true));
        } else {
            builder.refreshToken(refresh);
        }
        return builder.build();
    }

    // ─── finding 4: /oauth2/revoke must reach the session ───────────────

    @Test
    @DisplayName("revoking the refresh token cascades to the bound session")
    void refreshTokenRevocation_revokesSession() {
        OAuth2Authorization before = authorization("rt-1", false);
        OAuth2Authorization after = authorization("rt-1", true);
        when(delegate.findById("auth-1")).thenReturn(before);

        service.save(after);

        verify(cascadeListener).revokeBoundSession(after, "TOKEN_REVOKED");
    }

    @Test
    @DisplayName("an ordinary save does not revoke anything")
    void ordinarySave_doesNotRevoke() {
        OAuth2Authorization unchanged = authorization("rt-1", false);
        when(delegate.findById("auth-1")).thenReturn(authorization("rt-1", false));

        service.save(unchanged);

        verify(cascadeListener, never()).revokeBoundSession(any(), anyString());
    }

    @Test
    @DisplayName("a rotation tombstones the old token but does not revoke the session")
    void rotation_tombstonesWithoutRevoking() {
        when(delegate.findById("auth-1")).thenReturn(authorization("rt-1", false));
        OAuth2Authorization rotated = authorization("rt-2", false);

        service.save(rotated);

        verify(reuseGuard).recordRotation(any(), eq(rotated));
        verify(cascadeListener, never()).revokeBoundSession(any(), anyString());
    }

    @Test
    @DisplayName("an already-invalidated token is not revoked twice")
    void alreadyInvalidated_doesNotRevokeAgain() {
        when(delegate.findById("auth-1")).thenReturn(authorization("rt-1", true));

        service.save(authorization("rt-1", true));

        verify(cascadeListener, never()).revokeBoundSession(any(), anyString());
    }

    // ─── finding 5: replay detection belongs to the grant ───────────────

    @Test
    @DisplayName("a rotated-out token presented to the TOKEN endpoint is a replay")
    void missAtTokenEndpoint_detectsReplay() {
        servingRequestTo(TOKEN_ENDPOINT);
        when(delegate.findByToken("old-rt", OAuth2TokenType.REFRESH_TOKEN)).thenReturn(null);

        service.findByToken("old-rt", OAuth2TokenType.REFRESH_TOKEN);

        verify(reuseGuard).detectReplay("old-rt");
    }

    @Test
    @DisplayName("the same token presented to /oauth2/revoke is a no-op, not a replay")
    void missAtRevocationEndpoint_isNotAReplay() {
        servingRequestTo("/oauth2/revoke");
        when(delegate.findByToken("old-rt", OAuth2TokenType.REFRESH_TOKEN)).thenReturn(null);

        service.findByToken("old-rt", OAuth2TokenType.REFRESH_TOKEN);

        verify(reuseGuard, never()).detectReplay(anyString());
    }

    @Test
    @DisplayName("introspection of a rotated-out token does not kill the session either")
    void missAtIntrospectionEndpoint_isNotAReplay() {
        servingRequestTo("/oauth2/introspect");
        when(delegate.findByToken("old-rt", OAuth2TokenType.REFRESH_TOKEN)).thenReturn(null);

        service.findByToken("old-rt", OAuth2TokenType.REFRESH_TOKEN);

        verify(reuseGuard, never()).detectReplay(anyString());
    }

    @Test
    @DisplayName("a forwarded prefix on the token endpoint is still the token endpoint")
    void forwardedPrefix_stillCountsAsTheGrant() {
        servingRequestTo("/igrp-access-management/oauth2/token");
        when(delegate.findByToken("old-rt", OAuth2TokenType.REFRESH_TOKEN)).thenReturn(null);

        service.findByToken("old-rt", OAuth2TokenType.REFRESH_TOKEN);

        verify(reuseGuard).detectReplay("old-rt");
    }

    @Test
    @DisplayName("outside any request there is no grant to protect")
    void noRequestContext_doesNotDetect() {
        when(delegate.findByToken("old-rt", OAuth2TokenType.REFRESH_TOKEN)).thenReturn(null);

        service.findByToken("old-rt", OAuth2TokenType.REFRESH_TOKEN);

        verify(reuseGuard, never()).detectReplay(anyString());
    }

    @Test
    @DisplayName("a token that resolves is never treated as a replay")
    void hit_isNotAReplay() {
        servingRequestTo(TOKEN_ENDPOINT);
        when(delegate.findByToken("rt-1", OAuth2TokenType.REFRESH_TOKEN))
                .thenReturn(authorization("rt-1", false));

        service.findByToken("rt-1", OAuth2TokenType.REFRESH_TOKEN);

        verify(reuseGuard, never()).detectReplay(anyString());
    }

    // ─── remove() keeps its own cascade ─────────────────────────────────

    @Test
    @DisplayName("remove still cascades, with its own reason")
    void remove_stillCascades() {
        OAuth2Authorization authorization = authorization("rt-1", false);

        service.remove(authorization);

        verify(delegate).remove(authorization);
        verify(cascadeListener).onAuthorizationRemoved(authorization);
    }
}
