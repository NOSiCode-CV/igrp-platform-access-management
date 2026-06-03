package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import cv.igrp.platform.access_management.session.domain.constants.SessionStatus;
import cv.igrp.platform.access_management.session.domain.event.SessionRevokedEvent;
import cv.igrp.platform.access_management.session.domain.service.SessionHeartbeatService;
import cv.igrp.platform.access_management.session.infrastructure.audit.SessionAuditLogger;
import cv.igrp.platform.access_management.session.infrastructure.cache.SessionCacheEvictService;
import cv.igrp.platform.access_management.session.infrastructure.persistence.entity.SessionEntity;
import cv.igrp.platform.access_management.session.infrastructure.persistence.repository.SessionRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase E3 — OIDC RP-initiated logout cascade.
 *
 * <p>Wired as the {@code logoutResponseHandler} on
 * {@code OidcLogoutEndpointConfigurer}. When {@code /connect/logout} succeeds
 * we mark the bound {@link SessionEntity} as {@link SessionStatus#REVOKED}
 * with reason {@code USER_LOGOUT}, evict caches, publish
 * {@link SessionRevokedEvent}, and remove the underlying
 * {@link OAuth2Authorization} so subsequent JWT introspections /
 * resource-server requests are denied immediately by the enforcement chain.
 *
 * <p>The default redirect behavior of the OIDC logout endpoint is preserved:
 * when {@code post_logout_redirect_uri} was supplied we issue a 302 to it;
 * otherwise we return a bare 200.
 */
@Component
public class SessionLogoutHandler implements AuthenticationSuccessHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionLogoutHandler.class);

    /**
     * Cookies the Spring Authorization Server chain plants on the user agent
     * during the authentication flow. We clear every one of them on logout so
     * the browser never replays a stale credential to /oauth2/authorize after
     * the server-side session was invalidated.
     */
    private static final String[] CLEARABLE_COOKIES = { "JSESSIONID", "SESSION", "XSRF-TOKEN" };

    private final SessionRepository sessionRepository;
    private final SessionCacheEvictService sessionCacheEvictService;
    private final SessionHeartbeatService heartbeatService;
    private final OAuth2AuthorizationService authorizationService;
    private final ApplicationEventPublisher eventPublisher;
    private final SessionAuditLogger sessionAuditLogger;
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
    private final SecurityContextLogoutHandler springLogoutHandler = new SecurityContextLogoutHandler();

    /**
     * Holds the optional upstream-IdP ClientRegistrationRepository (only
     * present when {@code igrp.oauth.external-idp.enabled=true}). We resolve
     * it lazily so logout still works when federation is disabled.
     */
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider;

    /**
     * Registration id of the upstream IdP we should chain logout through.
     * Defaults to {@code external-idp} to match the
     * {@code spring.security.oauth2.client.registration.external-idp.*}
     * keys in application.properties.
     */
    private final String upstreamRegistrationId;

    /**
     * Toggle to opt out of upstream-IdP logout chaining (e.g. when the IdP
     * advertises an {@code end_session_endpoint} but does not actually honor
     * RP-initiated logout, or when local-only logout is the desired UX).
     */
    private final boolean cascadeLogoutToIdp;

    public SessionLogoutHandler(SessionRepository sessionRepository,
                                SessionCacheEvictService sessionCacheEvictService,
                                SessionHeartbeatService heartbeatService,
                                OAuth2AuthorizationService authorizationService,
                                ApplicationEventPublisher eventPublisher,
                                SessionAuditLogger sessionAuditLogger,
                                ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider,
                                @Value("${igrp.oauth.external-idp.registration-id:external-idp}") String upstreamRegistrationId,
                                @Value("${igrp.oauth.external-idp.logout-cascade:true}") boolean cascadeLogoutToIdp) {
        this.sessionRepository = sessionRepository;
        this.sessionCacheEvictService = sessionCacheEvictService;
        this.heartbeatService = heartbeatService;
        this.authorizationService = authorizationService;
        this.eventPublisher = eventPublisher;
        this.sessionAuditLogger = sessionAuditLogger;
        this.clientRegistrationRepositoryProvider = clientRegistrationRepositoryProvider;
        this.upstreamRegistrationId = upstreamRegistrationId;
        this.cascadeLogoutToIdp = cascadeLogoutToIdp;
    }

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OidcLogoutAuthenticationToken token = (OidcLogoutAuthenticationToken) authentication;

        OidcIdToken idToken = token.getIdToken();

        // 1. iGRP-side cleanup — load the bound SessionEntity ONCE so we can
        //    extract the upstream id_token (needed for the cascade hint
        //    below) before the revoke step writes back. WSO2 IS refuses to
        //    honor post_logout_redirect_uri without id_token_hint, and that
        //    hint is the IdP's original id_token stashed at issuance time.
        UUID sid = extractSid(idToken);
        Optional<SessionEntity> sessionOpt = sid != null
                ? sessionRepository.findBySessionId(sid)
                : Optional.empty();
        String upstreamIdToken = sessionOpt.map(SessionEntity::getUpstreamIdToken).orElse(null);
        revokeBoundSession(sid);

        // 2. Spring AS-side cleanup — remove the OAuth2Authorization so any
        //    refresh-token / introspection replay is denied.
        removeAuthorization(idToken);

        // 3. Spring Security cleanup — invalidate the HttpSession that backs
        //    the user's SecurityContext and clear SecurityContextHolder.
        //    Without this step the JSESSIONID cookie keeps the user
        //    authenticated for the *next* /oauth2/authorize call: Spring AS
        //    sees an already-authenticated session and silently issues a new
        //    authorization code, skipping the credential prompt. This is the
        //    root cause of the "logout doesn't actually log the user out"
        //    bug reported by the front-end integration team.
        invalidateSpringSession(request, response, token);

        // 4. Defence-in-depth — drop every cookie planted by the AS chain so
        //    no stale credential survives in the user agent.
        clearAuthCookies(request, response);

        String postLogoutRedirectUri = token.getPostLogoutRedirectUri();
        String state = token.getState();

        // 5. Cascade to the upstream IdP's end_session_endpoint so the user's
        //    SSO session at the IdP is invalidated too. Without this hop,
        //    /oauth2/authorize will silently re-authenticate the user against
        //    the IdP's still-active SSO cookie and Spring AS will hand back
        //    a fresh authorization code without prompting — the symptom the
        //    front-end team reported once the iGRP-side cleanup was working.
        //    The IdP, after killing its own session, redirects back to the
        //    caller's original post_logout_redirect_uri (plus state if any).
        String upstreamLogoutUrl = resolveUpstreamLogoutUrl(postLogoutRedirectUri, state, upstreamIdToken);
        if (upstreamLogoutUrl != null) {
            // Surface the actual redirect target at INFO so it shows up in
            // production logs by default — this is the single most useful
            // datapoint when an IdP refuses to honor post_logout_redirect_uri
            // (you can paste the URL straight into a browser to reproduce).
            // id_token_hint is masked because it's a multi-kB JWT containing
            // user PII; the full unmasked URL is still emitted at DEBUG.
            LOGGER.info("External IDP Logout: redirect URL = {}", maskIdTokenHint(upstreamLogoutUrl));
            LOGGER.debug("External IDP Logout: redirect URL (full) = {}", upstreamLogoutUrl);
            redirectStrategy.sendRedirect(request, response, upstreamLogoutUrl);
            return;
        }

        if (StringUtils.hasText(postLogoutRedirectUri)) {
            String location = StringUtils.hasText(state)
                    ? appendQueryParam(postLogoutRedirectUri, "state", state)
                    : postLogoutRedirectUri;
            redirectStrategy.sendRedirect(request, response, location);
            return;
        }
        response.setStatus(HttpServletResponse.SC_OK);
    }

    /**
     * Build the redirect URL to the upstream IdP's RP-initiated logout
     * endpoint, or {@code null} when chaining is disabled, the IdP isn't
     * configured, or its OIDC discovery doc didn't expose
     * {@code end_session_endpoint}.
     *
     * <p>We pass {@code client_id} (our registration at the IdP),
     * {@code post_logout_redirect_uri} (the caller's final destination), and
     * {@code id_token_hint} (the upstream IdP's original id_token, captured
     * at federated-login time and stashed on the SessionEntity). The hint is
     * critical for strict IdPs like WSO2 Identity Server which refuse to
     * honor post_logout_redirect_uri when it's missing, falling back to a
     * built-in "logged out" page instead of redirecting back to the caller.
     * Lenient IdPs (Keycloak ≥18, Authelia, Auth0) also accept it; never
     * harmful to include.
     */
    private String resolveUpstreamLogoutUrl(String finalRedirectUri, String state, String upstreamIdToken) {
        if (!cascadeLogoutToIdp) {
            return null;
        }
        ClientRegistrationRepository repo = clientRegistrationRepositoryProvider.getIfAvailable();
        if (repo == null) {
            return null;
        }
        ClientRegistration registration = repo.findByRegistrationId(upstreamRegistrationId);
        if (registration == null) {
            LOGGER.debug("OIDC logout: upstream registrationId '{}' not found, skipping IdP cascade",
                    upstreamRegistrationId);
            return null;
        }
        Object endSessionEndpoint = registration.getProviderDetails()
                .getConfigurationMetadata()
                .get("end_session_endpoint");
        if (endSessionEndpoint == null
                || !StringUtils.hasText(endSessionEndpoint.toString())) {
            LOGGER.debug("OIDC logout: upstream IdP {} did not advertise end_session_endpoint, "
                    + "skipping IdP cascade", upstreamRegistrationId);
            return null;
        }

        UriComponentsBuilder url = UriComponentsBuilder.fromUriString(endSessionEndpoint.toString());
        url.queryParam("client_id", registration.getClientId());
        if (StringUtils.hasText(upstreamIdToken)) {
            url.queryParam("id_token_hint", upstreamIdToken);
        } else {
            // WARN, not DEBUG — strict IdPs (WSO2 IS, Autentika, older Keycloak)
            // refuse to honor post_logout_redirect_uri without id_token_hint
            // and fall back to their built-in "logged out" page. The most
            // common cause is a session created BEFORE the V9 deploy that
            // added t_user_session.upstream_id_token + the JwtTokenConfig
            // capture: pre-V9 sessions can never have the column populated
            // because we don't have a copy of the IdP's id_token to backfill
            // with. The fix is to re-login (the new session captures it) and
            // the symptom self-resolves as the pre-V9 cohort cycles out.
            LOGGER.warn("External IDP Logout: no upstream id_token on file for this session — "
                    + "id_token_hint will be OMITTED. Strict IdPs (Autentika, WSO2 IS) will "
                    + "ignore post_logout_redirect_uri and show their built-in 'logged out' "
                    + "page. Most likely cause: this session was issued before the upstream-"
                    + "id-token capture was deployed (re-login to fix).");
        }
        if (StringUtils.hasText(finalRedirectUri)) {
            url.queryParam("post_logout_redirect_uri", finalRedirectUri);
        }
        if (StringUtils.hasText(state)) {
            url.queryParam("state", state);
        }
        // encode() before build() so : and / in post_logout_redirect_uri are
        // percent-escaped exactly once.
        return url.encode().build().toUriString();
    }

    private void invalidateSpringSession(HttpServletRequest request,
                                         HttpServletResponse response,
                                         Authentication authentication) {
        // SecurityContextLogoutHandler.logout() does three things:
        //  - request.getSession(false) → invalidate() if present
        //  - SecurityContextHolder.clearContext()
        //  - clear remember-me / persistent-token services
        // Pass the OidcLogoutAuthenticationToken — only used for audit logging
        // inside the handler; the invalidation itself derives from request.
        try {
            springLogoutHandler.logout(request, response, authentication);
        } catch (Exception ex) {
            LOGGER.warn("OIDC logout: SecurityContextLogoutHandler.logout failed: {}", ex.getMessage());
        }
        // Defensive: even if the handler above ran, force-invalidate to be
        // sure (the auth-server chain uses HttpSessionSecurityContextRepository).
        HttpSession session = request.getSession(false);
        if (session != null) {
            try {
                session.invalidate();
            } catch (IllegalStateException already) {
                // Already invalidated by the logout handler — ignore.
            }
        }
        SecurityContextHolder.clearContext();
    }

    private void clearAuthCookies(HttpServletRequest request, HttpServletResponse response) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return;
        }
        // Render the Set-Cookie header manually rather than going through
        // jakarta.servlet.http.Cookie: the standard Servlet API does NOT
        // expose SameSite, and OWASP-hardened session cookies are emitted
        // with SameSite=Lax. A deletion cookie that omits SameSite is
        // treated by modern browsers as a *different* cookie and the
        // original survives — so /oauth2/authorize keeps seeing a live
        // session even though we set Max-Age=0 on a same-named cookie.
        //
        // The Path / Domain / Secure / HttpOnly / SameSite combination
        // below mirrors the attributes Spring Boot sets via
        // server.servlet.session.cookie.* in application.properties.
        boolean secure = isHttps(request);
        for (Cookie cookie : cookies) {
            for (String name : CLEARABLE_COOKIES) {
                if (name.equalsIgnoreCase(cookie.getName())) {
                    StringBuilder header = new StringBuilder(96)
                            .append(cookie.getName()).append("=")
                            .append("; Path=/")
                            .append("; Max-Age=0")
                            .append("; HttpOnly")
                            .append("; SameSite=Lax");
                    if (secure) {
                        header.append("; Secure");
                    }
                    response.addHeader("Set-Cookie", header.toString());
                }
            }
        }
    }

    /**
     * The request is HTTPS either directly or behind a TLS-terminating
     * reverse proxy. The proxy case is the normal one in production —
     * `server.forward-headers-strategy=framework` (set by the OWASP
     * commit) makes Spring trust X-Forwarded-Proto, but at this layer
     * we read it ourselves so the Secure attribute is emitted whenever
     * the user-agent's hop is over TLS.
     */
    private static boolean isHttps(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        String proto = request.getHeader("X-Forwarded-Proto");
        return proto != null && proto.equalsIgnoreCase("https");
    }

    private void revokeBoundSession(UUID sid) {
        if (sid == null) {
            return;
        }
        Optional<SessionEntity> entity = sessionRepository.findBySessionId(sid);
        if (entity.isEmpty()) {
            heartbeatService.evict(sid);
            return;
        }
        SessionEntity session = entity.get();
        if (SessionStatus.ACTIVE.equals(session.getStatus())) {
            session.revoke("USER_LOGOUT", "USER");
            sessionRepository.save(session);
        }
        heartbeatService.evict(sid);
        if (session.getUserId() != null) {
            sessionCacheEvictService.evictBySubject(session.getUserId());
        }
        sessionAuditLogger.recordRevoked(session.getSessionId(), session.getUserId(),
                "USER_LOGOUT", SessionAuditLogger.USER);
        eventPublisher.publishEvent(new SessionRevokedEvent(
                session.getSessionId(),
                session.getUserId(),
                "USER_LOGOUT",
                "USER"
        ));
        LOGGER.info("OIDC logout: revoked session {} (user={}) with reason USER_LOGOUT",
                sid, session.getUserId());
    }

    private void removeAuthorization(OidcIdToken idToken) {
        if (idToken == null) {
            return;
        }
        OAuth2Authorization authorization = authorizationService.findByToken(
                idToken.getTokenValue(), null);
        if (authorization != null) {
            authorizationService.remove(authorization);
        }
    }

    private static UUID extractSid(OidcIdToken idToken) {
        if (idToken == null) {
            return null;
        }
        Map<String, Object> claims = idToken.getClaims();
        if (claims == null) {
            return null;
        }
        Object raw = claims.get("sid");
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw.toString());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String appendQueryParam(String url, String name, String value) {
        char separator = url.contains("?") ? '&' : '?';
        return url + separator + name + "=" + value;
    }

    /**
     * Mask the {@code id_token_hint} query-parameter value before logging so
     * the user's IdP-issued JWT (which contains PII) isn't dumped into log
     * aggregation. Replaces the value with {@code <jwt-XX-chars>} so size
     * is still visible for triage. Everything else in the URL is preserved.
     */
    private static String maskIdTokenHint(String url) {
        if (url == null) {
            return null;
        }
        int start = url.indexOf("id_token_hint=");
        if (start < 0) {
            return url;
        }
        int valueStart = start + "id_token_hint=".length();
        int valueEnd = url.indexOf('&', valueStart);
        if (valueEnd < 0) {
            valueEnd = url.length();
        }
        int valueLen = valueEnd - valueStart;
        return url.substring(0, valueStart)
                + "<jwt-" + valueLen + "-chars>"
                + url.substring(valueEnd);
    }
}
