package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import cv.igrp.platform.access_management.session.domain.constants.SessionStatus;
import cv.igrp.platform.access_management.session.domain.event.SessionRevokedEvent;
import cv.igrp.platform.access_management.session.domain.service.SessionHeartbeatService;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationToken;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-level coverage for the OIDC RP-initiated logout cascade. Asserts the
 * happy-path side effects (session revoke, cache evict, event publish, auth
 * removal, redirect) and the no-op branches that protect against double-revoke
 * and missing/non-iGRP id tokens.
 */
@ExtendWith(MockitoExtension.class)
class SessionLogoutHandlerTest {

    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private SessionCacheEvictService sessionCacheEvictService;
    @Mock
    private SessionHeartbeatService heartbeatService;
    @Mock
    private OAuth2AuthorizationService authorizationService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private cv.igrp.platform.access_management.session.infrastructure.audit.SessionAuditLogger sessionAuditLogger;

    private SessionLogoutHandler handler;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        handler = new SessionLogoutHandler(sessionRepository, sessionCacheEvictService,
                heartbeatService, authorizationService, eventPublisher, sessionAuditLogger);
        request = new MockHttpServletRequest("POST", "/connect/logout");
        response = new MockHttpServletResponse();
    }

    @Test
    void revokesActiveSession_evictsCache_publishesEvent_andRedirects() throws Exception {
        UUID sid = UUID.randomUUID();
        OidcIdToken idToken = idTokenWithSid(sid);
        SessionEntity session = activeSession(sid, "00000000-0000-0000-0000-000000000099");
        when(sessionRepository.findBySessionId(sid)).thenReturn(Optional.of(session));
        OAuth2Authorization authorization = stubAuthorization(idToken);
        when(authorizationService.findByToken(idToken.getTokenValue(), null)).thenReturn(authorization);

        handler.onAuthenticationSuccess(request, response,
                logoutToken(idToken, "https://app.igrp/post-logout", "xyz"));

        ArgumentCaptor<SessionEntity> saved = ArgumentCaptor.forClass(SessionEntity.class);
        verify(sessionRepository).save(saved.capture());
        assertEquals(SessionStatus.REVOKED, saved.getValue().getStatus());
        assertEquals("USER_LOGOUT", saved.getValue().getClosedReason());
        assertEquals("USER", saved.getValue().getClosedBy());

        verify(heartbeatService).evict(sid);
        verify(sessionCacheEvictService).evictBySubject("00000000-0000-0000-0000-000000000099");
        verify(eventPublisher).publishEvent(any(SessionRevokedEvent.class));
        verify(authorizationService).remove(authorization);

        assertEquals(302, response.getStatus());
        assertEquals("https://app.igrp/post-logout?state=xyz", response.getRedirectedUrl());
    }

    @Test
    void noPostLogoutRedirect_yields200() throws Exception {
        UUID sid = UUID.randomUUID();
        OidcIdToken idToken = idTokenWithSid(sid);
        SessionEntity session = activeSession(sid, "00000000-0000-0000-0000-000000000099");
        when(sessionRepository.findBySessionId(sid)).thenReturn(Optional.of(session));
        when(authorizationService.findByToken(idToken.getTokenValue(), null)).thenReturn(null);

        handler.onAuthenticationSuccess(request, response, logoutToken(idToken, null, null));

        assertEquals(200, response.getStatus());
    }

    @Test
    void doesNotResaveAlreadyRevokedSession_butStillEvictsAndPublishes() throws Exception {
        UUID sid = UUID.randomUUID();
        OidcIdToken idToken = idTokenWithSid(sid);
        SessionEntity session = activeSession(sid, "00000000-0000-0000-0000-000000000099");
        session.setStatus(SessionStatus.REVOKED);
        when(sessionRepository.findBySessionId(sid)).thenReturn(Optional.of(session));

        handler.onAuthenticationSuccess(request, response, logoutToken(idToken, null, null));

        verify(sessionRepository, never()).save(any(SessionEntity.class));
        verify(heartbeatService).evict(sid);
        verify(sessionCacheEvictService).evictBySubject("00000000-0000-0000-0000-000000000099");
        verify(eventPublisher).publishEvent(any(SessionRevokedEvent.class));
    }

    @Test
    void noSidClaim_isSafelyIgnoredButStillRespondsAndRemovesAuthorization() throws Exception {
        OidcIdToken idToken = idToken(Map.of("sub", "42"));
        OAuth2Authorization authorization = stubAuthorization(idToken);
        when(authorizationService.findByToken(idToken.getTokenValue(), null)).thenReturn(authorization);

        handler.onAuthenticationSuccess(request, response, logoutToken(idToken, null, null));

        verify(sessionRepository, never()).findBySessionId(any());
        verify(authorizationService).remove(authorization);
        assertEquals(200, response.getStatus());
    }

    @Test
    void sessionNotFound_evictsCacheKeyButDoesNotSave() throws Exception {
        UUID sid = UUID.randomUUID();
        OidcIdToken idToken = idTokenWithSid(sid);
        when(sessionRepository.findBySessionId(sid)).thenReturn(Optional.empty());

        handler.onAuthenticationSuccess(request, response, logoutToken(idToken, null, null));

        verify(sessionRepository, never()).save(any(SessionEntity.class));
        verify(heartbeatService).evict(sid);
        verify(sessionCacheEvictService, never()).evictBySubject(eq("00000000-0000-0000-0000-000000000099"));
    }

    private SessionEntity activeSession(UUID sid, String userId) {
        SessionEntity entity = new SessionEntity();
        entity.setSessionId(sid);
        entity.setUserId(userId);
        entity.setStatus(SessionStatus.ACTIVE);
        entity.setStartedAt(Instant.now().minusSeconds(60));
        entity.setLastSeenAt(Instant.now());
        entity.setExpiresAt(Instant.now().plusSeconds(60));
        entity.setAbsoluteExpiresAt(Instant.now().plusSeconds(3600));
        return entity;
    }

    private OidcIdToken idTokenWithSid(UUID sid) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "42");
        claims.put("sid", sid.toString());
        return idToken(claims);
    }

    private OidcIdToken idToken(Map<String, Object> claims) {
        return new OidcIdToken(
                "tok-" + UUID.randomUUID(),
                Instant.now().minusSeconds(30),
                Instant.now().plusSeconds(30),
                claims);
    }

    private OAuth2Authorization stubAuthorization(OidcIdToken idToken) {
        RegisteredClient client = RegisteredClient.withId("test-client")
                .clientId("test-client")
                .clientAuthenticationMethod(org.springframework.security.oauth2.core.ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://app/cb")
                .build();
        return OAuth2Authorization.withRegisteredClient(client)
                .principalName("42")
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .token(idToken)
                .build();
    }

    private OidcLogoutAuthenticationToken logoutToken(OidcIdToken idToken,
                                                      String postLogoutRedirectUri,
                                                      String state) {
        return new OidcLogoutAuthenticationToken(
                idToken,
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("42", "n/a"),
                "JSESSIONID-test",
                "test-client",
                postLogoutRedirectUri,
                state);
    }
}
