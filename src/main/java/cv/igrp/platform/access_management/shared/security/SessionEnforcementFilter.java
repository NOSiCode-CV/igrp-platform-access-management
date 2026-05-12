package cv.igrp.platform.access_management.shared.security;

import cv.igrp.platform.access_management.session.config.SessionProperties;
import cv.igrp.platform.access_management.session.domain.constants.SessionStatus;
import cv.igrp.platform.access_management.session.domain.service.SessionHeartbeatService;
import cv.igrp.platform.access_management.session.infrastructure.metrics.SessionMetrics;
import cv.igrp.platform.access_management.session.infrastructure.persistence.entity.SessionEntity;
import cv.igrp.platform.access_management.session.infrastructure.persistence.repository.SessionRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase C1 — Server-side session enforcement.
 *
 * <p>Runs on the OAuth2 resource-server chain right after the
 * {@code BearerTokenAuthenticationFilter}. For every authenticated JWT carrying
 * a {@code sid} claim we look up the bound {@link SessionEntity} (Redis-first,
 * DB fallback) and reject the request when the session is not ACTIVE or has
 * passed its absolute lifetime — making "kill session = effective immediately"
 * true regardless of the JWT's own {@code exp}.
 *
 * <p>Path-based skip (FR-11): M2M ({@code /api/m2m/**}), Swagger/actuator,
 * authorization-server endpoints ({@code /oauth2/**}, {@code /connect/**},
 * {@code /.well-known/**}, {@code /login}, {@code /userinfo}) and
 * {@code /api/session/check} bypass the filter entirely.
 *
 * <p>FR-13: tokens that reach an enforced path WITHOUT a {@code sid} claim are
 * rejected with 401 ({@code error_description="missing_sid"}). There is no
 * legacy fallback — Phase B made {@code sid} mandatory at issuance for every
 * non-{@code client_credentials} grant in {@code JwtTokenConfig.igrpTokenCustomizer},
 * and M2M {@code client_credentials} tokens only travel over {@code /api/m2m/**}
 * which is skipped above. Any sid-less JWT seen here is either misissued or
 * forged.
 *
 * <p>Behind a feature flag {@code igrp.session.enforcement-enabled}
 * (default {@code true}) so the chain can be disabled in an emergency.
 */
@Component
public class SessionEnforcementFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionEnforcementFilter.class);

    private static final String CLAIM_SID = "sid";
    private static final List<String> SKIPPED_PREFIXES = List.of(
            "/api/m2m/",
            "/api/session/check",
            "/v3/api-docs",
            "/swagger-ui",
            "/swagger-resources",
            "/webjars",
            "/actuator",
            "/oauth2/",
            "/connect/",
            "/.well-known/",
            "/login",
            "/userinfo"
    );

    private final SessionRepository sessionRepository;
    private final SessionHeartbeatService heartbeatService;
    private final SessionProperties sessionProperties;
    private final IGRPUserEntityRepository userRepository;
    private final SessionMetrics sessionMetrics;
    private final boolean enforcementEnabled;

    public SessionEnforcementFilter(SessionRepository sessionRepository,
                                    SessionHeartbeatService heartbeatService,
                                    SessionProperties sessionProperties,
                                    IGRPUserEntityRepository userRepository,
                                    SessionMetrics sessionMetrics,
                                    @Value("${igrp.session.enforcement-enabled:true}") boolean enforcementEnabled) {
        this.sessionRepository = sessionRepository;
        this.heartbeatService = heartbeatService;
        this.sessionProperties = sessionProperties;
        this.userRepository = userRepository;
        this.sessionMetrics = sessionMetrics;
        this.enforcementEnabled = enforcementEnabled;
        LOGGER.info("SessionEnforcementFilter initialized (enforcementEnabled={})", enforcementEnabled);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enforcementEnabled) {
            return true;
        }
        String uri = request.getRequestURI();
        for (String prefix : SKIPPED_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = extractJwt(authentication);

        // Unauthenticated or non-JWT auth (e.g. anonymous) — let downstream rules decide.
        if (jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String sidClaim = jwt.getClaimAsString(CLAIM_SID);
        if (sidClaim == null || sidClaim.isBlank()) {
            // FR-13: no legacy fallback. M2M tokens (the only legitimate sid-less
            // issuance branch in JwtTokenConfig) only travel over /api/m2m/** and
            // are already filtered out by shouldNotFilter() above. A sid-less JWT
            // reaching this point is either misissued or forged — reject.
            sessionMetrics.recordRejectedRevoked("missing_sid");
            unauthorized(response, "invalid_token", "missing_sid");
            return;
        }

        UUID sid;
        try {
            sid = UUID.fromString(sidClaim);
        } catch (IllegalArgumentException ex) {
            unauthorized(response, "invalid_token", "Malformed session identifier");
            return;
        }

        Instant now = Instant.now();

        // F1 — reject any JWT issued before the user-wide validity floor
        // (set on password reset / forced re-auth).
        Integer userIdFromToken = parseUserId(jwt.getSubject());
        if (userIdFromToken != null) {
            Instant iat = jwt.getIssuedAt();
            Instant floor = userRepository.findTokensNotValidBeforeById(userIdFromToken).orElse(null);
            if (floor != null && iat != null && iat.isBefore(floor)) {
                sessionMetrics.recordRejectedRevoked("tokens_invalidated");
                unauthorized(response, "invalid_token", "tokens_invalidated");
                return;
            }
        }

        // Hot path: Redis snapshot.
        Optional<SessionHeartbeatService.Snapshot> cached = heartbeatService.findCached(sid);
        if (cached.isPresent()) {
            String denial = denialReason(cached.get().status(),
                    cached.get().expiresAt(), cached.get().absoluteExpiresAt(), now);
            if (denial != null) {
                sessionMetrics.recordRejectedRevoked(denial);
                unauthorized(response, "invalid_token", denial);
                return;
            }
            // Cold-load entity only when we actually need to write the heartbeat.
            if (heartbeatRequired(cached.get().lastSeenAt(), now)) {
                touchFromDb(sid, now);
            }
            sessionMetrics.recordHeartbeat();
            filterChain.doFilter(request, response);
            return;
        }

        // Cache miss → load from DB.
        SessionEntity session;
        try {
            session = sessionRepository.findBySessionId(sid).orElse(null);
        } catch (DataAccessException ex) {
            // Risk mitigation (plan §6): on data-store outage prefer 503 over a
            // 401 storm that would force every user to re-login.
            LOGGER.error("Session enforcement: data store unavailable for sid={}", sid, ex);
            response.reset();
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setHeader("Retry-After", "5");
            response.flushBuffer();
            return;
        }

        if (session == null) {
            sessionMetrics.recordRejectedRevoked("session_revoked");
            unauthorized(response, "invalid_token", "session_revoked");
            return;
        }

        String denial = denialReason(session.getStatus(),
                session.getExpiresAt(), session.getAbsoluteExpiresAt(), now);
        if (denial != null) {
            sessionMetrics.recordRejectedRevoked(denial);
            unauthorized(response, "invalid_token", denial);
            return;
        }

        heartbeatService.cache(session);
        heartbeatService.touch(session, now, sessionProperties.getHeartbeatDebounceSeconds());
        sessionMetrics.recordHeartbeat();

        filterChain.doFilter(request, response);
    }

    private static Integer parseUserId(String sub) {
        if (sub == null || sub.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(sub);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean heartbeatRequired(Instant lastSeenAt, Instant now) {
        if (lastSeenAt == null) {
            return true;
        }
        long debounce = Math.max(1L, sessionProperties.getHeartbeatDebounceSeconds());
        return !lastSeenAt.plusSeconds(debounce).isAfter(now);
    }

    private void touchFromDb(UUID sid, Instant now) {
        try {
            sessionRepository.findBySessionId(sid).ifPresent(entity ->
                    heartbeatService.touch(entity, now, sessionProperties.getHeartbeatDebounceSeconds()));
        } catch (DataAccessException ex) {
            LOGGER.warn("Heartbeat: cannot reload sid={} for last_seen_at update: {}",
                    sid, ex.getMessage());
        }
    }

    private static String denialReason(SessionStatus status,
                                       Instant expiresAt,
                                       Instant absoluteExpiresAt,
                                       Instant now) {
        if (!SessionStatus.ACTIVE.equals(status)) {
            return "session_revoked";
        }
        if (absoluteExpiresAt != null && !now.isBefore(absoluteExpiresAt)) {
            return "session_expired";
        }
        if (expiresAt != null && !now.isBefore(expiresAt)) {
            return "session_expired";
        }
        return null;
    }

    private static Jwt extractJwt(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        return null;
    }

    /**
     * Commit a 401 response immediately so downstream filters and entry points
     * (notably {@code BearerTokenAuthenticationEntryPoint}) cannot rewrite the
     * {@code WWW-Authenticate} header or replace the status. We deliberately
     * avoid {@link HttpServletResponse#sendError(int)} because that re-enters
     * the container error-page pipeline which clears headers; we set the
     * status + header + a tiny RFC-7807-ish JSON body, then {@code flushBuffer()}
     * to make the response immutable from this point forward.
     */
    private static void unauthorized(HttpServletResponse response,
                                     String error,
                                     String description) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.reset();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate",
                "Bearer error=\"" + error + "\", error_description=\"" + description + "\"");
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("application/json;charset=UTF-8");
        String body = "{\"error\":\"" + error
                + "\",\"error_description\":\"" + description
                + "\"}";
        response.getWriter().write(body);
        response.getWriter().flush();
        response.flushBuffer();
    }
}
