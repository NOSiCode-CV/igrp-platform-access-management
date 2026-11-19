package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.RefreshTokenTombstoneEntity;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.RefreshTokenTombstoneRepository;
import cv.igrp.platform.access_management.security_audit.application.service.AccessAuditLogger;
import cv.igrp.platform.access_management.shared.domain.events.UserAccessEndedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * FR-8, #33: the guard tombstones a rotated refresh token at save time and, when a rotated
 * value comes back, ends the user's access on the whole platform.
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenReuseGuardTest {

    private static final String USER = "00000000-0000-0000-0000-000000000042";

    @Mock
    private RefreshTokenTombstoneRepository tombstoneRepository;
    @Mock
    private UserAuthorizations userAuthorizations;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private AccessAuditLogger accessAuditLogger;

    private RefreshTokenReuseGuard guard;

    @BeforeEach
    void setUp() {
        guard = new RefreshTokenReuseGuard(tombstoneRepository, userAuthorizations, eventPublisher, accessAuditLogger);
    }

    @Test
    void recordRotation_tombstonesTheOldTokenWithTheUser() {
        when(tombstoneRepository.findByTokenHash(RefreshTokenReuseGuard.sha256("old-refresh")))
                .thenReturn(Optional.empty());

        guard.recordRotation(authorizationWith("old-refresh", USER), authorizationWith("new-refresh", USER));

        ArgumentCaptor<RefreshTokenTombstoneEntity> captor = ArgumentCaptor.forClass(RefreshTokenTombstoneEntity.class);
        verify(tombstoneRepository).save(captor.capture());
        RefreshTokenTombstoneEntity saved = captor.getValue();
        assertThat(saved.getTokenHash()).isEqualTo(RefreshTokenReuseGuard.sha256("old-refresh"));
        assertThat(saved.getSessionId()).isNull();
        assertThat(saved.getUserId()).isEqualTo(USER);
        assertThat(saved.getInvalidatedAt()).isNotNull();
        assertThat(saved.getExpiresAt()).isNotNull();
    }

    @Test
    void recordRotation_aStoreFailureDoesNotFailTheRotation() {
        when(tombstoneRepository.findByTokenHash(any())).thenThrow(new DataAccessResourceFailureException("down"));

        guard.recordRotation(authorizationWith("old-refresh", USER), authorizationWith("new-refresh", USER));

        verify(tombstoneRepository).findByTokenHash(any());
    }

    @Test
    void recordRotation_noOpWhenTokenValueUnchanged() {
        guard.recordRotation(authorizationWith("same", USER), authorizationWith("same", USER));

        verifyNoInteractions(tombstoneRepository);
    }

    @Test
    void recordRotation_noOpWhenPreviousHasNoRefreshToken() {
        guard.recordRotation(authorizationAccessOnly(USER), authorizationWith("new-refresh", USER));

        verifyNoInteractions(tombstoneRepository);
    }

    @Test
    void detectReplay_returnsFalseWhenNoTombstone() {
        when(tombstoneRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThat(guard.detectReplay("unknown")).isFalse();
        assertThat(guard.detectReplay(" ")).isFalse();
        verifyNoInteractions(userAuthorizations, eventPublisher, accessAuditLogger);
    }

    @Test
    void detectReplay_endsTheUsersAccess() {
        when(tombstoneRepository.findByTokenHash(RefreshTokenReuseGuard.sha256("replayed")))
                .thenReturn(Optional.of(tombstone(USER)));

        assertThat(guard.detectReplay("replayed")).isTrue();

        verify(userAuthorizations).removeAll(USER);
        verify(accessAuditLogger).recordAccessEnded(USER, RefreshTokenReuseGuard.REVOKE_REASON, AccessAuditLogger.SYSTEM);
        verify(eventPublisher).publishEvent(new UserAccessEndedEvent(Set.of(USER), RefreshTokenReuseGuard.REVOKE_REASON));
    }

    @Test
    void detectReplay_withoutAUserIsStillAReplay() {
        when(tombstoneRepository.findByTokenHash(any())).thenReturn(Optional.of(tombstone(null)));

        assertThat(guard.detectReplay("replayed")).isTrue();
        verifyNoInteractions(userAuthorizations, eventPublisher, accessAuditLogger);
    }

    @Test
    void detectReplay_aFailingSideEffectStillReportsTheReplay() {
        when(tombstoneRepository.findByTokenHash(any())).thenReturn(Optional.of(tombstone(USER)));
        when(userAuthorizations.removeAll(USER)).thenThrow(new DataAccessResourceFailureException("down"));

        assertThat(guard.detectReplay("replayed")).isTrue();
    }

    // ---- helpers ----

    private static RefreshTokenTombstoneEntity tombstone(String userId) {
        return new RefreshTokenTombstoneEntity(RefreshTokenReuseGuard.sha256("replayed"), null, userId,
                Instant.now().minusSeconds(10), Instant.now().plusSeconds(3600));
    }

    private static OAuth2Authorization authorizationWith(String refreshValue, String principal) {
        Instant now = Instant.now();
        return OAuth2Authorization.withRegisteredClient(client())
                .principalName(principal)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .token(access(now))
                .refreshToken(new OAuth2RefreshToken(refreshValue, now, now.plusSeconds(7200)))
                .build();
    }

    private static OAuth2Authorization authorizationAccessOnly(String principal) {
        return OAuth2Authorization.withRegisteredClient(client())
                .principalName(principal)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .token(access(Instant.now()))
                .build();
    }

    private static OAuth2AccessToken access(Instant now) {
        return new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access", now, now.plusSeconds(3600));
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
