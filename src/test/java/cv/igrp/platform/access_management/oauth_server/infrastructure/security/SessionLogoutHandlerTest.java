package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import cv.igrp.platform.access_management.session.domain.constants.SessionStatus;
import cv.igrp.platform.access_management.session.domain.event.SessionRevokedEvent;
import cv.igrp.platform.access_management.session.domain.service.SessionHeartbeatService;
import cv.igrp.platform.access_management.session.domain.service.SsoSessionCascadeService;
import cv.igrp.platform.access_management.session.infrastructure.cache.SessionCacheEvictService;
import cv.igrp.platform.access_management.session.infrastructure.persistence.entity.SessionEntity;
import cv.igrp.platform.access_management.session.infrastructure.persistence.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
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
    @Mock
    private SsoSessionCascadeService ssoSessionCascadeService;

    private static final String USER_ID = "00000000-0000-0000-0000-000000000099";

    private SessionLogoutHandler handler;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        // Federation isn't configured in this unit test, so the IdP-cascade
        // resolver gets an ObjectProvider that yields null — the handler
        // falls back to the direct redirect path the existing assertions
        // were written against.
        ObjectProvider<ClientRegistrationRepository> noClientRegistration = emptyProvider();
        handler = new SessionLogoutHandler(sessionRepository, sessionCacheEvictService,
                heartbeatService, authorizationService, eventPublisher, sessionAuditLogger,
                ssoSessionCascadeService, noClientRegistration, "external-idp", true);
        request = new MockHttpServletRequest("POST", "/connect/logout");
        response = new MockHttpServletResponse();
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> emptyProvider() {
        ObjectProvider<T> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        org.mockito.Mockito.when(provider.getIfAvailable()).thenReturn(null);
        return provider;
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

    // ─── R2.1-R2.4: the logout cascade ──────────────────────────────────────

    @Test
    void cascadesToSiblingSessionsSharingTheSignIn() throws Exception {
        UUID sid = UUID.randomUUID();
        OidcIdToken idToken = idTokenWithSid(sid);
        SessionEntity session = activeSession(sid, USER_ID);
        session.setSsoSessionKey("hashed-sign-in-key");
        when(sessionRepository.findBySessionId(sid)).thenReturn(Optional.of(session));

        handler.onAuthenticationSuccess(request, response, logoutToken(idToken, null, null));

        // The bound sid is passed so the cascade skips it rather than revoking
        // it twice and duplicating its audit entry and event.
        verify(ssoSessionCascadeService).revokeSiblings(USER_ID, "hashed-sign-in-key", sid);
    }

    @Test
    void doesNotCascadeWhenTheSessionHasNoSignInKey() throws Exception {
        // Rows created before R1.1, and any grant with no browser sign-in behind
        // it, have no key. They must keep the pre-cascade behaviour of revoking
        // one row (R1.5/N2) — not cascade on a null key, which would match every
        // other keyless row the user owns.
        UUID sid = UUID.randomUUID();
        OidcIdToken idToken = idTokenWithSid(sid);
        SessionEntity session = activeSession(sid, USER_ID);
        session.setSsoSessionKey(null);
        when(sessionRepository.findBySessionId(sid)).thenReturn(Optional.of(session));

        handler.onAuthenticationSuccess(request, response, logoutToken(idToken, null, null));

        verify(ssoSessionCascadeService, never()).revokeSiblings(any(), any(), any());
        // The bound row is still revoked.
        verify(sessionRepository).save(any(SessionEntity.class));
    }

    @Test
    void cascadeFailureDoesNotBreakTheRestOfLogout() throws Exception {
        // R2.4. The cascade is the one step whose failure must not take the
        // others with it: the bound row stays revoked, the authorization is
        // still removed and the user still gets their redirect. Degrading to the
        // old single-app behaviour beats failing the sign-out outright.
        UUID sid = UUID.randomUUID();
        OidcIdToken idToken = idTokenWithSid(sid);
        SessionEntity session = activeSession(sid, USER_ID);
        session.setSsoSessionKey("hashed-sign-in-key");
        when(sessionRepository.findBySessionId(sid)).thenReturn(Optional.of(session));
        OAuth2Authorization authorization = stubAuthorization(idToken);
        when(authorizationService.findByToken(idToken.getTokenValue(), null)).thenReturn(authorization);
        when(ssoSessionCascadeService.revokeSiblings(any(), any(), any()))
                .thenThrow(new RuntimeException("database unavailable"));

        handler.onAuthenticationSuccess(request, response,
                logoutToken(idToken, "https://app.igrp/post-logout", "xyz"));

        ArgumentCaptor<SessionEntity> saved = ArgumentCaptor.forClass(SessionEntity.class);
        verify(sessionRepository).save(saved.capture());
        assertEquals(SessionStatus.REVOKED, saved.getValue().getStatus());
        verify(authorizationService).remove(authorization);
        assertEquals(302, response.getStatus());
        assertEquals("https://app.igrp/post-logout?state=xyz", response.getRedirectedUrl());
    }

    @Test
    void doesNotCascadeWhenTheBoundSessionIsMissing() throws Exception {
        UUID sid = UUID.randomUUID();
        when(sessionRepository.findBySessionId(sid)).thenReturn(Optional.empty());

        handler.onAuthenticationSuccess(request, response,
                logoutToken(idTokenWithSid(sid), null, null));

        // No row means no key to cascade on. Guessing from the user alone would
        // sign them out of their other browsers, which D1 explicitly rules out.
        verify(ssoSessionCascadeService, never()).revokeSiblings(any(), any(), any());
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
