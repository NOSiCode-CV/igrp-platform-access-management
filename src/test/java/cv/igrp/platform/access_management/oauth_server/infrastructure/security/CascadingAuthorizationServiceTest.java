package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FR-8 — verifies the {@link CascadingAuthorizationService} wrapper hooks into
 * both rotation (on {@code save}) and replay detection (on
 * {@code findByToken(REFRESH_TOKEN)} miss). The C3 revocation cascade is also
 * exercised — see {@link #remove_cascadesIntoRevocationListener()}.
 */
@ExtendWith(MockitoExtension.class)
class CascadingAuthorizationServiceTest {

    @Mock
    private OAuth2AuthorizationService delegate;
    @Mock
    private RevocationCascadeListener revocationCascadeListener;
    @Mock
    private RefreshTokenReuseGuard refreshTokenReuseGuard;

    private CascadingAuthorizationService wrapper;

    @BeforeEach
    void setUp() {
        wrapper = new CascadingAuthorizationService(
                delegate, revocationCascadeListener, refreshTokenReuseGuard);
    }

    @Test
    void save_callsGuardWhenRefreshTokenRotated() {
        OAuth2Authorization previous = authWith("old-refresh", "auth-1");
        OAuth2Authorization next = authWith("new-refresh", "auth-1");
        when(delegate.findById("auth-1")).thenReturn(previous);

        wrapper.save(next);

        verify(delegate).save(next);
        verify(refreshTokenReuseGuard).recordRotation(previous, next);
    }

    @Test
    void save_doesNotCallGuardWhenTokenValueUnchanged() {
        OAuth2Authorization previous = authWith("same", "auth-1");
        OAuth2Authorization next = authWith("same", "auth-1");
        when(delegate.findById("auth-1")).thenReturn(previous);

        wrapper.save(next);

        verify(delegate).save(next);
        verify(refreshTokenReuseGuard, never()).recordRotation(any(), any());
    }

    @Test
    void save_doesNotCallGuardOnFirstIssuance() {
        OAuth2Authorization next = authWith("first-refresh", "auth-1");
        when(delegate.findById("auth-1")).thenReturn(null);

        wrapper.save(next);

        verify(delegate).save(next);
        verify(refreshTokenReuseGuard, never()).recordRotation(any(), any());
    }

    @Test
    void findByToken_triggersReplayDetectionOnRefreshTokenMiss() {
        when(delegate.findByToken(eq("replayed"), eq(OAuth2TokenType.REFRESH_TOKEN))).thenReturn(null);

        wrapper.findByToken("replayed", OAuth2TokenType.REFRESH_TOKEN);

        verify(refreshTokenReuseGuard).detectReplay("replayed");
    }

    @Test
    void findByToken_doesNotTriggerDetectionWhenMatchFound() {
        OAuth2Authorization auth = authWith("rt", "auth-1");
        when(delegate.findByToken(eq("rt"), eq(OAuth2TokenType.REFRESH_TOKEN))).thenReturn(auth);

        wrapper.findByToken("rt", OAuth2TokenType.REFRESH_TOKEN);

        verify(refreshTokenReuseGuard, never()).detectReplay(any());
    }

    @Test
    void findByToken_doesNotTriggerDetectionOnAccessTokenMiss() {
        when(delegate.findByToken(eq("at"), eq(OAuth2TokenType.ACCESS_TOKEN))).thenReturn(null);

        wrapper.findByToken("at", OAuth2TokenType.ACCESS_TOKEN);

        verify(refreshTokenReuseGuard, never()).detectReplay(any());
    }

    @Test
    void remove_cascadesIntoRevocationListener() {
        OAuth2Authorization auth = authWith("rt", "auth-1");

        wrapper.remove(auth);

        verify(delegate).remove(auth);
        verify(revocationCascadeListener).onAuthorizationRemoved(auth);
    }

    private static OAuth2Authorization authWith(String refreshValue, String id) {
        Instant now = Instant.now();
        OAuth2RefreshToken refresh = new OAuth2RefreshToken(refreshValue, now, now.plusSeconds(7200));
        OAuth2AccessToken access = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "access-" + refreshValue, now, now.plusSeconds(3600));
        return OAuth2Authorization.withRegisteredClient(client())
                .id(id)
                .principalName("42")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .accessToken(access)
                .refreshToken(refresh)
                .build();
    }

    private static RegisteredClient client() {
        return RegisteredClient.withId("test-client")
                .clientId("test-client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("https://app/cb")
                .build();
    }
}
