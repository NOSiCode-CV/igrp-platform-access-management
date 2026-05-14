package cv.igrp.platform.access_management.shared.security;

import cv.igrp.platform.access_management.session.config.SessionProperties;
import cv.igrp.platform.access_management.session.domain.service.SessionHeartbeatService;
import cv.igrp.platform.access_management.session.infrastructure.metrics.SessionMetrics;
import cv.igrp.platform.access_management.session.infrastructure.persistence.repository.SessionRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * T-C3 / FR-13 — A JWT that is otherwise valid (signed by the same key, fresh
 * iat/exp, parseable user-id subject) but missing the {@code sid} claim MUST be
 * rejected by the enforcement filter on every enforced path. No legacy
 * fallback. Phase B made {@code sid} mandatory at issuance, so any sid-less
 * token reaching the filter is misissued or forged.
 */
@ExtendWith(MockitoExtension.class)
class EnforcementMissingSidTest {

    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private SessionHeartbeatService heartbeatService;
    @Mock
    private IGRPUserEntityRepository userRepository;
    @Mock
    private SessionMetrics sessionMetrics;
    @Mock
    private FilterChain filterChain;

    private SessionEnforcementFilter filter;
    private SessionProperties sessionProperties;

    @BeforeEach
    void setUp() {
        sessionProperties = new SessionProperties();
        filter = new SessionEnforcementFilter(
                sessionRepository,
                heartbeatService,
                sessionProperties,
                userRepository,
                sessionMetrics,
                true);
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsSidlessJwtOnEnforcedPathWith401AndMissingSidChallenge() throws ServletException, IOException {
        Jwt jwt = sidlessJwtForUser("42");
        authenticate(jwt);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        String challenge = response.getHeader("WWW-Authenticate");
        assertNotNull(challenge, "WWW-Authenticate header must be set on 401");
        assertTrue(challenge.startsWith("Bearer "), () -> "Bearer scheme expected, got: " + challenge);
        assertTrue(challenge.contains("error=\"invalid_token\""),
                () -> "invalid_token error code expected, got: " + challenge);
        assertTrue(challenge.contains("error_description=\"missing_sid\""),
                () -> "missing_sid description expected, got: " + challenge);

        // Filter chain must NOT proceed; no session lookup must happen for a sid-less token.
        verifyNoInteractions(filterChain);
        verifyNoInteractions(sessionRepository);
        verifyNoInteractions(heartbeatService);
        verify(sessionMetrics).recordRejectedRevoked("missing_sid");
    }

    @Test
    void blankSidIsTreatedAsMissing() throws ServletException, IOException {
        Jwt jwt = jwtWithRawSid("42", "   ");
        authenticate(jwt);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getHeader("WWW-Authenticate").contains("missing_sid"));
        verifyNoInteractions(filterChain);
        verify(sessionMetrics).recordRejectedRevoked("missing_sid");
    }

    @Test
    void m2mPathStillBypassesEnforcementEvenForSidlessTokens() throws ServletException, IOException {
        // Belt-and-suspenders: even if M2M traffic somehow carried a sid-less JWT,
        // the URL allow-list MUST keep skipping it (FR-11) — we only tightened the
        // sid check on enforced paths.
        Jwt jwt = sidlessJwtForUser("99");
        authenticate(jwt);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/m2m/permissions/sync");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        // shouldNotFilter() short-circuits → 200 default, chain proceeds, no metrics.
        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(sessionMetrics);
        verifyNoInteractions(sessionRepository);
        verifyNoInteractions(heartbeatService);
    }

    @Test
    void rejectsRevokedSessionForUserTokenWrappedInOidcContextAuthenticationToken() throws ServletException, IOException {
        // Regression: live FR-20 cascade test showed /api/users/me kept returning
        // 200 after /connect/logout because extractJwt() didn't recognise the
        // OidcContextAuthenticationToken shape (principal = IgrpOidcUser, jwt held
        // as credentials) produced by IgrpJwtAuthenticationConverter. The filter
        // took the "no JWT — let downstream rules decide" branch and bypassed
        // enforcement entirely for every user token issued via authorization_code.
        java.util.UUID sid = java.util.UUID.fromString("de45113b-cf30-4349-876e-4e93c685a373");
        Jwt jwt = jwtWithRawSid("0ab33988-489d-440a-b99d-5ff0aab21262", sid.toString());

        // Wrap exactly as IgrpJwtAuthenticationConverter would.
        org.springframework.security.oauth2.core.oidc.OidcIdToken idToken =
                new org.springframework.security.oauth2.core.oidc.OidcIdToken(
                        jwt.getTokenValue(),
                        jwt.getIssuedAt(),
                        jwt.getExpiresAt(),
                        jwt.getClaims());
        IgrpOidcUser oidcUser = new IgrpOidcUser(java.util.List.of(), idToken, null);
        OidcContextAuthenticationToken auth = new OidcContextAuthenticationToken(
                oidcUser, jwt, java.util.List.of());
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);

        // Simulate a REVOKED session in the DB (cache miss → DB fallback).
        org.mockito.Mockito.when(heartbeatService.findCached(sid))
                .thenReturn(java.util.Optional.empty());
        cv.igrp.platform.access_management.session.infrastructure.persistence.entity.SessionEntity revoked =
                new cv.igrp.platform.access_management.session.infrastructure.persistence.entity.SessionEntity();
        revoked.setSessionId(sid);
        revoked.setStatus(cv.igrp.platform.access_management.session.domain.constants.SessionStatus.REVOKED);
        org.mockito.Mockito.when(sessionRepository.findBySessionId(sid))
                .thenReturn(java.util.Optional.of(revoked));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        // Must reject — NOT pass through silently as it did before the fix.
        assertEquals(401, response.getStatus(), "Revoked session must yield 401 for user-token shape too");
        String challenge = response.getHeader("WWW-Authenticate");
        assertNotNull(challenge);
        assertTrue(challenge.contains("session_revoked") || challenge.contains("session_expired"),
                () -> "Expected revoked/expired challenge, got: " + challenge);
        verifyNoInteractions(filterChain);
    }

    @Test
    void anonymousRequestIsNotConsideredSidlessAndLetsChainHandleIt() throws ServletException, IOException {
        // No JWT on the security context → filter delegates downstream rather
        // than rejecting with missing_sid (which is reserved for authenticated
        // JWTs only).
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(sessionMetrics);
        verifyNoInteractions(sessionRepository);
    }

    // --- helpers -----------------------------------------------------------

    private static Jwt sidlessJwtForUser(String userId) {
        Map<String, Object> headers = Map.of("alg", "RS256", "kid", "igrp-oauth-key");
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", userId);
        claims.put("iss", "http://localhost:8080");
        claims.put("jti", "test-jti-" + userId);
        // Intentionally no "sid" claim.
        Instant now = Instant.now();
        return new Jwt("forged.but.signed.token", now, now.plusSeconds(300), headers, claims);
    }

    private static Jwt jwtWithRawSid(String userId, String rawSid) {
        Map<String, Object> headers = Map.of("alg", "RS256", "kid", "igrp-oauth-key");
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", userId);
        claims.put("iss", "http://localhost:8080");
        claims.put("jti", "test-jti-" + userId);
        claims.put("sid", rawSid);
        Instant now = Instant.now();
        return new Jwt("forged.but.signed.token", now, now.plusSeconds(300), headers, claims);
    }

    private static void authenticate(Jwt jwt) {
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
    }
}
