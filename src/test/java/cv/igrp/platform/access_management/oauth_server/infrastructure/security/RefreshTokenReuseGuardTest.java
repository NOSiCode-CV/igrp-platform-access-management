package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.RefreshTokenTombstoneEntity;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.RefreshTokenTombstoneRepository;
import cv.igrp.platform.access_management.session.domain.constants.SessionStatus;
import cv.igrp.platform.access_management.session.domain.event.SessionRevokedEvent;
import cv.igrp.platform.access_management.session.infrastructure.cache.SessionCacheEvictService;
import cv.igrp.platform.access_management.session.infrastructure.persistence.entity.SessionEntity;
import cv.igrp.platform.access_management.session.infrastructure.persistence.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * FR-8 — unit coverage for {@link RefreshTokenReuseGuard}. The guard hashes
 * the rotated refresh-token value at save time and consults the tombstone
 * table on every refresh-token lookup miss, revoking the linked session and
 * publishing {@link SessionRevokedEvent} on hit.
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenReuseGuardTest {

    @Mock
    private RefreshTokenTombstoneRepository tombstoneRepository;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private SessionCacheEvictService sessionCacheEvictService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private RefreshTokenReuseGuard guard;

    @BeforeEach
    void setUp() {
        guard = new RefreshTokenReuseGuard(
                tombstoneRepository, sessionRepository, sessionCacheEvictService, eventPublisher);
    }

    @Test
    void recordRotation_tombstonesOldTokenWithSidAndUserId() {
        UUID sid = UUID.randomUUID();
        OAuth2Authorization previous = authorizationWith("old-refresh", "access-1", sid, "42");
        OAuth2Authorization next = authorizationWith("new-refresh", "access-2", sid, "42");
        when(tombstoneRepository.findByTokenHash(RefreshTokenReuseGuard.sha256("old-refresh")))
                .thenReturn(Optional.empty());

        guard.recordRotation(previous, next);

        ArgumentCaptor<RefreshTokenTombstoneEntity> captor =
                ArgumentCaptor.forClass(RefreshTokenTombstoneEntity.class);
        verify(tombstoneRepository).save(captor.capture());
        RefreshTokenTombstoneEntity saved = captor.getValue();
        assertThat(saved.getTokenHash()).isEqualTo(RefreshTokenReuseGuard.sha256("old-refresh"));
        assertThat(saved.getSessionId()).isEqualTo(sid);
        assertThat(saved.getUserId()).isEqualTo(42);
        assertThat(saved.getInvalidatedAt()).isNotNull();
        assertThat(saved.getExpiresAt()).isNotNull();
    }

    @Test
    void recordRotation_noOpWhenTokenValueUnchanged() {
        UUID sid = UUID.randomUUID();
        OAuth2Authorization previous = authorizationWith("same", "access-1", sid, "42");
        OAuth2Authorization next = authorizationWith("same", "access-2", sid, "42");

        guard.recordRotation(previous, next);

        verifyNoInteractions(tombstoneRepository);
    }

    @Test
    void recordRotation_noOpWhenPreviousHasNoRefreshToken() {
        UUID sid = UUID.randomUUID();
        OAuth2Authorization previous = authorizationAccessOnly("access-1", sid, "42");
        OAuth2Authorization next = authorizationWith("new-refresh", "access-2", sid, "42");

        guard.recordRotation(previous, next);

        verifyNoInteractions(tombstoneRepository);
    }

    @Test
    void detectReplay_returnsFalseWhenNoTombstone() {
        when(tombstoneRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        boolean detected = guard.detectReplay("unknown");

        assertThat(detected).isFalse();
        verifyNoInteractions(sessionRepository);
        verifyNoInteractions(sessionCacheEvictService);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void detectReplay_revokesActiveSessionAndPublishesEvent() {
        UUID sid = UUID.randomUUID();
        RefreshTokenTombstoneEntity tombstone = new RefreshTokenTombstoneEntity(
                RefreshTokenReuseGuard.sha256("replayed"), sid, 99,
                Instant.now().minusSeconds(10), Instant.now().plusSeconds(3600));
        when(tombstoneRepository.findByTokenHash(RefreshTokenReuseGuard.sha256("replayed")))
                .thenReturn(Optional.of(tombstone));
        SessionEntity session = new SessionEntity();
        session.setSessionId(sid);
        session.setUserId(99);
        session.setStatus(SessionStatus.ACTIVE);
        session.setStartedAt(Instant.now().minusSeconds(60));
        session.setLastSeenAt(Instant.now());
        session.setExpiresAt(Instant.now().plusSeconds(60));
        when(sessionRepository.findBySessionId(sid)).thenReturn(Optional.of(session));

        boolean detected = guard.detectReplay("replayed");

        assertThat(detected).isTrue();
        ArgumentCaptor<SessionEntity> saved = ArgumentCaptor.forClass(SessionEntity.class);
        verify(sessionRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(SessionStatus.REVOKED);
        assertThat(saved.getValue().getClosedReason()).isEqualTo("REFRESH_TOKEN_REUSE");
        assertThat(saved.getValue().getClosedBy()).isEqualTo("SYSTEM");
        verify(sessionCacheEvictService).evictBySubject(99);
        ArgumentCaptor<SessionRevokedEvent> event = ArgumentCaptor.forClass(SessionRevokedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().getSessionId()).isEqualTo(sid);
        assertThat(event.getValue().getUserId()).isEqualTo(99);
        assertThat(event.getValue().getReason()).isEqualTo("REFRESH_TOKEN_REUSE");
    }

    @Test
    void detectReplay_doesNotResaveAlreadyRevokedSessionButStillPublishes() {
        UUID sid = UUID.randomUUID();
        RefreshTokenTombstoneEntity tombstone = new RefreshTokenTombstoneEntity(
                RefreshTokenReuseGuard.sha256("replayed"), sid, 99,
                Instant.now().minusSeconds(10), Instant.now().plusSeconds(3600));
        when(tombstoneRepository.findByTokenHash(RefreshTokenReuseGuard.sha256("replayed")))
                .thenReturn(Optional.of(tombstone));
        SessionEntity session = new SessionEntity();
        session.setSessionId(sid);
        session.setUserId(99);
        session.setStatus(SessionStatus.REVOKED);
        session.setStartedAt(Instant.now().minusSeconds(60));
        session.setLastSeenAt(Instant.now());
        session.setExpiresAt(Instant.now().plusSeconds(60));
        when(sessionRepository.findBySessionId(sid)).thenReturn(Optional.of(session));

        guard.detectReplay("replayed");

        verify(sessionRepository, never()).save(any(SessionEntity.class));
        verify(sessionCacheEvictService).evictBySubject(99);
        verify(eventPublisher).publishEvent(any(SessionRevokedEvent.class));
    }

    @Test
    void detectReplay_missingSessionRowStillPublishesEventAndEvicts() {
        UUID sid = UUID.randomUUID();
        RefreshTokenTombstoneEntity tombstone = new RefreshTokenTombstoneEntity(
                RefreshTokenReuseGuard.sha256("replayed"), sid, 99,
                Instant.now().minusSeconds(10), Instant.now().plusSeconds(3600));
        when(tombstoneRepository.findByTokenHash(RefreshTokenReuseGuard.sha256("replayed")))
                .thenReturn(Optional.of(tombstone));
        when(sessionRepository.findBySessionId(sid)).thenReturn(Optional.empty());

        boolean detected = guard.detectReplay("replayed");

        assertThat(detected).isTrue();
        verify(sessionRepository, never()).save(any(SessionEntity.class));
        verify(sessionCacheEvictService).evictBySubject(99);
        verify(eventPublisher).publishEvent(any(SessionRevokedEvent.class));
    }

    // ---- helpers ----

    private static OAuth2Authorization authorizationWith(String refreshValue,
                                                         String accessValue,
                                                         UUID sid,
                                                         String principal) {
        Instant now = Instant.now();
        OAuth2RefreshToken refresh = new OAuth2RefreshToken(refreshValue, now, now.plusSeconds(7200));
        OAuth2AccessToken access = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, accessValue, now, now.plusSeconds(3600));
        return OAuth2Authorization.withRegisteredClient(client())
                .principalName(principal)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .token(access, metadata -> metadata.put(
                        OAuth2Authorization.Token.CLAIMS_METADATA_NAME,
                        Map.of("sid", sid.toString())))
                .refreshToken(refresh)
                .build();
    }

    private static OAuth2Authorization authorizationAccessOnly(String accessValue, UUID sid, String principal) {
        Instant now = Instant.now();
        OAuth2AccessToken access = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, accessValue, now, now.plusSeconds(3600));
        return OAuth2Authorization.withRegisteredClient(client())
                .principalName(principal)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .token(access, metadata -> metadata.put(
                        OAuth2Authorization.Token.CLAIMS_METADATA_NAME,
                        Map.of("sid", sid.toString())))
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
