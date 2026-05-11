package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import cv.igrp.platform.access_management.session.domain.constants.SessionStatus;
import cv.igrp.platform.access_management.session.infrastructure.persistence.entity.SessionEntity;
import cv.igrp.platform.access_management.session.infrastructure.persistence.repository.SessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenIntrospection;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenIntrospectionAuthenticationToken;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Verifies the introspection override never weakens the contract for tokens
 * that were already inactive or that have no session binding, and forces
 * {@code active=false} when the bound session is dead — covering the FR-19
 * acceptance scenarios on top of the structural JWT signature validation that
 * Spring Authorization Server's default provider already performs upstream.
 */
@ExtendWith(MockitoExtension.class)
class SessionAwareIntrospectorTest {

    @Mock
    private SessionRepository sessionRepository;

    private SessionAwareIntrospector handler;
    private HttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        handler = new SessionAwareIntrospector(sessionRepository);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    void passThrough_whenAlreadyInactive() throws IOException {
        OAuth2TokenIntrospection input = OAuth2TokenIntrospection.builder().build();
        handler.onAuthenticationSuccess(request, response, tokenWith(input));

        assertFalse(parseActive(response));
        verifyNoInteractions(sessionRepository);
    }

    @Test
    void passThrough_whenNoSidClaim_doesNotConsultRepository() throws IOException {
        OAuth2TokenIntrospection input = OAuth2TokenIntrospection.builder()
                .active(true)
                .clientId("m2m-client")
                .build();
        handler.onAuthenticationSuccess(request, response, tokenWith(input));

        assertTrue(parseActive(response));
        verify(sessionRepository, never()).findBySessionId(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void inactive_whenSessionMissing() throws IOException {
        UUID sid = UUID.randomUUID();
        OAuth2TokenIntrospection input = active(sid);
        when(sessionRepository.findBySessionId(sid)).thenReturn(Optional.empty());

        handler.onAuthenticationSuccess(request, response, tokenWith(input));

        assertFalse(parseActive(response));
    }

    @Test
    void inactive_whenSessionRevoked() throws IOException {
        UUID sid = UUID.randomUUID();
        OAuth2TokenIntrospection input = active(sid);
        when(sessionRepository.findBySessionId(sid))
                .thenReturn(Optional.of(session(SessionStatus.REVOKED, Instant.now().plusSeconds(60))));

        handler.onAuthenticationSuccess(request, response, tokenWith(input));

        assertFalse(parseActive(response));
    }

    @Test
    void inactive_whenSessionPastExpiry() throws IOException {
        UUID sid = UUID.randomUUID();
        OAuth2TokenIntrospection input = active(sid);
        when(sessionRepository.findBySessionId(sid))
                .thenReturn(Optional.of(session(SessionStatus.ACTIVE, Instant.now().minusSeconds(1))));

        handler.onAuthenticationSuccess(request, response, tokenWith(input));

        assertFalse(parseActive(response));
    }

    @Test
    void active_whenSessionHealthy() throws IOException {
        UUID sid = UUID.randomUUID();
        OAuth2TokenIntrospection input = active(sid);
        when(sessionRepository.findBySessionId(sid))
                .thenReturn(Optional.of(session(SessionStatus.ACTIVE, Instant.now().plusSeconds(60))));

        handler.onAuthenticationSuccess(request, response, tokenWith(input));

        assertTrue(parseActive(response));
    }

    @Test
    void inactive_whenSidClaimMalformed() throws IOException {
        OAuth2TokenIntrospection input = OAuth2TokenIntrospection.builder()
                .active(true)
                .claim("sid", "not-a-uuid")
                .build();
        handler.onAuthenticationSuccess(request, response, tokenWith(input));

        assertFalse(parseActive(response));
        verify(sessionRepository, never()).findBySessionId(org.mockito.ArgumentMatchers.any());
    }

    private OAuth2TokenIntrospection active(UUID sid) {
        return OAuth2TokenIntrospection.builder()
                .active(true)
                .claim("sid", sid.toString())
                .clientId("igrp-fe")
                .build();
    }

    private SessionEntity session(SessionStatus status, Instant expiresAt) {
        SessionEntity entity = new SessionEntity();
        entity.setSessionId(UUID.randomUUID());
        entity.setUserId(42);
        entity.setStatus(status);
        entity.setStartedAt(Instant.now().minusSeconds(60));
        entity.setLastSeenAt(Instant.now());
        entity.setExpiresAt(expiresAt);
        entity.setAbsoluteExpiresAt(Instant.now().plusSeconds(3600));
        return entity;
    }

    private OAuth2TokenIntrospectionAuthenticationToken tokenWith(OAuth2TokenIntrospection introspection) {
        return new OAuth2TokenIntrospectionAuthenticationToken(
                "opaque-token-value",
                new org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken(
                        "client",
                        org.springframework.security.oauth2.core.ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
                        "secret", null),
                introspection);
    }

    private static boolean parseActive(MockHttpServletResponse response) {
        String body;
        try {
            body = response.getContentAsString();
        } catch (java.io.UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
        return body.contains("\"active\":true");
    }
}
