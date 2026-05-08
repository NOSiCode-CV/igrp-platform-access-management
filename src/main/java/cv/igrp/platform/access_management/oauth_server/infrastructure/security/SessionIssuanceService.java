package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import cv.igrp.platform.access_management.session.config.SessionProperties;
import cv.igrp.platform.access_management.session.domain.constants.SessionStatus;
import cv.igrp.platform.access_management.session.infrastructure.cache.SessionCacheEvictService;
import cv.igrp.platform.access_management.session.infrastructure.metrics.SessionMetrics;
import cv.igrp.platform.access_management.session.infrastructure.persistence.entity.SessionEntity;
import cv.igrp.platform.access_management.session.infrastructure.persistence.repository.SessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Binds every issued JWT to a server-side {@link SessionEntity}.
 *
 * <p>Invoked from {@link JwtTokenConfig#igrpTokenCustomizer} during the
 * authorization-server token issuance pipeline. Two paths are supported:
 *
 * <ul>
 *   <li><b>New issuance</b> (auth-code, password, etc.) — atomically replaces
 *       any existing session for {@code (sub, device_id)}, evicts the oldest
 *       LRU entry when the per-user cap is hit, and persists a fresh
 *       {@link SessionEntity}.</li>
 *   <li><b>Refresh grant</b> — preserves the original {@code sid}, slides
 *       {@code expires_at} forward (capped by {@code absolute_expires_at}),
 *       updates {@code jti} and refreshes {@code last_seen_at}.</li>
 * </ul>
 *
 * <p>Adds the mandatory {@code sid} and {@code device_id} claims to the
 * outgoing JWT.
 */
@Service
public class SessionIssuanceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionIssuanceService.class);

    static final String CLAIM_SID = "sid";
    static final String CLAIM_DEVICE_ID = "device_id";
    static final String DEVICE_ID_HEADER = "X-Device-Id";

    private final SessionRepository sessionRepository;
    private final SessionCacheEvictService sessionCacheEvictService;
    private final SessionProperties sessionProperties;
    private final SessionMetrics sessionMetrics;

    public SessionIssuanceService(SessionRepository sessionRepository,
                                  SessionCacheEvictService sessionCacheEvictService,
                                  SessionProperties sessionProperties,
                                  SessionMetrics sessionMetrics) {
        this.sessionRepository = sessionRepository;
        this.sessionCacheEvictService = sessionCacheEvictService;
        this.sessionProperties = sessionProperties;
        this.sessionMetrics = sessionMetrics;
    }

    /**
     * Result of binding a token to a session — fed back into the JWT claim builder.
     */
    public record IssuanceBinding(UUID sid, String deviceId) {}

    /**
     * Bind the access token currently being issued to a session row.
     *
     * @param context        the in-flight {@link JwtEncodingContext}.
     * @param userId         the authenticated user's internal id.
     * @param clientId       the OAuth2 client requesting the token.
     * @param jti            the JWT id assigned to this access token.
     * @return the {@code (sid, device_id)} pair to expose as JWT claims.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IssuanceBinding bindAccessToken(JwtEncodingContext context,
                                           Integer userId,
                                           String clientId,
                                           String jti) {
        if (userId == null) {
            throw new IllegalStateException("Cannot bind a JWT to a session without a resolved user id");
        }

        HttpServletRequest request = currentRequest();
        String deviceId = resolveDeviceId(request, clientId);
        AuthorizationGrantType grantType = context.getAuthorizationGrantType();
        Instant now = Instant.now();
        long ttlSeconds = sessionProperties.getTimeoutSeconds();

        if (grantType != null && AuthorizationGrantType.REFRESH_TOKEN.equals(grantType)) {
            Optional<SessionEntity> existing = locateSessionForRefresh(context, userId, deviceId);
            if (existing.isPresent()) {
                SessionEntity refreshed = applyRefresh(existing.get(), jti, clientId, now, ttlSeconds);
                return new IssuanceBinding(refreshed.getSessionId(), refreshed.getDeviceId());
            }
            // Fall through: refresh path lost its anchor — issue a brand-new session.
            LOGGER.warn("Refresh token issuance for user={} device={} could not locate prior session; creating new",
                    userId, deviceId);
        }

        SessionEntity created = openNewSession(userId, deviceId, clientId, jti, request, now, ttlSeconds);
        return new IssuanceBinding(created.getSessionId(), created.getDeviceId());
    }

    private Optional<SessionEntity> locateSessionForRefresh(JwtEncodingContext context,
                                                            Integer userId,
                                                            String deviceId) {
        OAuth2Authorization authorization = context.getAuthorization();
        if (authorization != null && authorization.getAccessToken() != null) {
            Object previousSid = authorization.getAccessToken().getClaims() != null
                    ? authorization.getAccessToken().getClaims().get(CLAIM_SID)
                    : null;
            if (previousSid != null) {
                try {
                    UUID parsed = UUID.fromString(previousSid.toString());
                    Optional<SessionEntity> bySid = sessionRepository.findBySessionId(parsed);
                    if (bySid.isPresent()) {
                        return bySid;
                    }
                } catch (IllegalArgumentException ignored) {
                    // fall through to (sub, device) lookup
                }
            }
        }
        return sessionRepository.findByUserIdAndDeviceIdAndStatus(
                userId, deviceId, SessionStatus.ACTIVE);
    }

    private SessionEntity applyRefresh(SessionEntity session,
                                       String jti,
                                       String clientId,
                                       Instant now,
                                       long ttlSeconds) {
        if (!SessionStatus.ACTIVE.equals(session.getStatus())) {
            sessionMetrics.recordRefreshRejected("session_not_active");
            throw new SessionRefreshRejectedException(
                    "session_not_active", "Session " + session.getSessionId() + " is no longer active");
        }
        Instant absolute = session.getAbsoluteExpiresAt();
        if (absolute != null && !now.isBefore(absolute)) {
            session.expire();
            sessionRepository.save(session);
            sessionCacheEvictService.evictBySubject(session.getUserId());
            sessionMetrics.recordRefreshRejected("absolute_lifetime_exceeded");
            throw new SessionRefreshRejectedException(
                    "absolute_lifetime_exceeded",
                    "Session " + session.getSessionId() + " hit its absolute lifetime ceiling");
        }

        Instant slid = now.plusSeconds(ttlSeconds);
        if (absolute != null && slid.isAfter(absolute)) {
            slid = absolute;
        }
        session.setExpiresAt(slid);
        session.setLastSeenAt(now);
        session.setJti(jti);
        if (clientId != null) {
            session.setClientId(clientId);
        }
        SessionEntity saved = sessionRepository.save(session);
        sessionCacheEvictService.evictBySubject(saved.getUserId());
        LOGGER.debug("Refreshed session {} for sub={} device={} until {}",
                saved.getSessionId(), saved.getUserId(), saved.getDeviceId(), saved.getExpiresAt());
        return saved;
    }

    private SessionEntity openNewSession(Integer userId,
                                         String deviceId,
                                         String clientId,
                                         String jti,
                                         HttpServletRequest request,
                                         Instant now,
                                         long ttlSeconds) {
        // FR-1: atomically replace any existing (user, device_id) ACTIVE row.
        sessionRepository
                .findByUserIdAndDeviceIdAndStatus(userId, deviceId, SessionStatus.ACTIVE)
                .ifPresent(prev -> {
                    prev.close("SESSION_REPLACED", "SYSTEM");
                    sessionRepository.save(prev);
                });

        // FR-5: enforce the per-user cap with LRU eviction.
        int maxPerUser = Math.max(1, sessionProperties.getMaxPerUser());
        List<SessionEntity> active = sessionRepository
                .findByUserIdAndStatusOrderByLastSeenAtAsc(userId, SessionStatus.ACTIVE);
        int overflow = active.size() - (maxPerUser - 1);
        for (int i = 0; i < overflow && i < active.size(); i++) {
            SessionEntity victim = active.get(i);
            victim.close("SESSION_LIMIT_EXCEEDED", "SYSTEM");
            sessionRepository.save(victim);
            sessionMetrics.recordEvictedLru();
            LOGGER.info("LRU-evicted session {} for user={} (cap={})",
                    victim.getSessionId(), userId, maxPerUser);
        }

        SessionEntity entity = new SessionEntity();
        entity.setSessionId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setStatus(SessionStatus.ACTIVE);
        entity.setStartedAt(now);
        entity.setLastSeenAt(now);
        entity.setExpiresAt(now.plusSeconds(ttlSeconds));
        entity.setAbsoluteExpiresAt(now.plusSeconds(sessionProperties.getAbsoluteTimeoutSeconds()));
        entity.setDeviceId(deviceId);
        entity.setClientId(clientId);
        entity.setJti(jti);
        if (request != null) {
            entity.setClientIp(request.getRemoteAddr());
            entity.setUserAgentHash(hashUserAgent(request.getHeader("User-Agent")));
        }
        SessionEntity saved = sessionRepository.save(entity);
        sessionCacheEvictService.evictBySubject(userId);
        sessionMetrics.recordCreated();
        LOGGER.debug("Issued session {} for user={} device={} client={} jti={} exp={} abs={}",
                saved.getSessionId(), userId, deviceId, clientId, jti,
                saved.getExpiresAt(), saved.getAbsoluteExpiresAt());
        return saved;
    }

    /**
     * FR-4: device_id resolution. Prefers an explicit client-supplied header; otherwise
     * derives a deterministic SHA-256 hash of {@code (User-Agent, client_ip, client_id)}
     * so two requests from the same browser map to the same row.
     */
    String resolveDeviceId(HttpServletRequest request, String clientId) {
        if (request != null) {
            String supplied = request.getHeader(DEVICE_ID_HEADER);
            if (supplied != null && !supplied.isBlank()) {
                return supplied.length() > 128 ? supplied.substring(0, 128) : supplied;
            }
        }
        String userAgent = request != null ? request.getHeader("User-Agent") : null;
        String ip = request != null ? request.getRemoteAddr() : null;
        String seed = nullToEmpty(userAgent) + "|" + nullToEmpty(ip) + "|" + nullToEmpty(clientId);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(seed.getBytes(StandardCharsets.UTF_8));
            return "derived:" + Base64.getUrlEncoder().withoutPadding().encodeToString(hash).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JRE; this branch is unreachable.
            throw new IllegalStateException(e);
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    private String hashUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(userAgent.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * Signals that an in-flight refresh-grant must be rejected. The customizer
     * translates this into an OAuth2 {@code invalid_grant} error.
     */
    public static class SessionRefreshRejectedException extends RuntimeException {
        private final String errorCode;

        public SessionRefreshRejectedException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        public String getErrorCode() {
            return errorCode;
        }
    }
}
