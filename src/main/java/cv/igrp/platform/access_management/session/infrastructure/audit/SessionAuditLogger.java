package cv.igrp.platform.access_management.session.infrastructure.audit;

import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditService;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditCategory;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * NFR-4 — single fan-in point for "session state changed" audit rows. Every
 * transition site in the session lifecycle calls one of the {@code record*}
 * methods below; each delegates to {@link SecurityAuditService#logEvent} with
 * {@link AuditCategory#AUTHENTICATION} and a context bag carrying the
 * canonical {@code reason}, {@code actor}, {@code sid}, {@code sub} and
 * (where available) {@code deviceId} / {@code clientId}.
 *
 * <p>Calls are fire-and-forget: any failure (audit DB outage, JSON
 * serialization) is logged and swallowed so it never blocks the session
 * transition. {@link SecurityAuditServiceImpl#logEvent} is already wrapped in
 * {@code REQUIRES_NEW} + try/catch — the extra guard here keeps that contract
 * even if the implementation changes.
 *
 * <p>Actor conventions:
 * <ul>
 *   <li>{@link #SYSTEM} — schedulers, listeners, cascade hooks, replay guard.</li>
 *   <li>{@link #USER} — direct end-user action on their own session (token
 *       issuance, refresh, OIDC logout).</li>
 *   <li>{@link #adminActor(String)} — admin-driven action; the string carries
 *       the admin's external id.</li>
 * </ul>
 */
@Component
public class SessionAuditLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionAuditLogger.class);

    public static final String SYSTEM = "SYSTEM";
    public static final String USER = "USER";

    /** Build the canonical {@code ADMIN:{externalId}} actor token. */
    public static String adminActor(String externalId) {
        return "ADMIN:" + (externalId == null ? "unknown" : externalId);
    }

    private final SecurityAuditService securityAuditService;

    public SessionAuditLogger(SecurityAuditService securityAuditService) {
        this.securityAuditService = securityAuditService;
    }

    public void recordCreated(UUID sid, Integer userId, String deviceId, String clientId, String actor) {
        emit(AuditEventType.SESSION_CREATED, "NEW_LOGIN", actor, sid, userId, deviceId, clientId);
    }

    public void recordReplaced(UUID sid, Integer userId, String deviceId, String clientId, String actor) {
        emit(AuditEventType.SESSION_REPLACED, "SESSION_REPLACED", actor, sid, userId, deviceId, clientId);
    }

    public void recordLimitExceeded(UUID sid, Integer userId, String deviceId, String clientId) {
        emit(AuditEventType.SESSION_LIMIT_EXCEEDED, "SESSION_LIMIT_EXCEEDED",
                SYSTEM, sid, userId, deviceId, clientId);
    }

    public void recordRefreshed(UUID sid, Integer userId, String deviceId, String clientId, String actor) {
        emit(AuditEventType.SESSION_REFRESHED, "TOKEN_REFRESH", actor, sid, userId, deviceId, clientId);
    }

    public void recordRevoked(UUID sid, Integer userId, String reason, String actor) {
        emit(AuditEventType.SESSION_REVOKED, reason, actor, sid, userId, null, null);
    }

    public void recordExpired(UUID sid, Integer userId, String reason) {
        emit(AuditEventType.SESSION_EXPIRED, reason, SYSTEM, sid, userId, null, null);
    }

    public void recordForcedReauth(Integer userId, String actor) {
        emit(AuditEventType.SESSION_FORCED_REAUTH, "FORCED_REAUTH", actor, null, userId, null, null);
    }

    private void emit(AuditEventType type,
                      String reason,
                      String actor,
                      UUID sid,
                      Integer userId,
                      String deviceId,
                      String clientId) {
        try {
            Map<String, Object> ctx = new LinkedHashMap<>();
            ctx.put("reason", reason == null ? "UNSPECIFIED" : reason);
            ctx.put("actor", actor == null ? SYSTEM : actor);
            if (sid != null) {
                // Both keys: "sessionId" is the SecurityAuditLogEntity column;
                // "sid" is the JWT claim name used by the rest of the module.
                ctx.put("sessionId", sid.toString());
                ctx.put("sid", sid.toString());
            }
            if (userId != null) {
                ctx.put("userId", userId.toString());
                ctx.put("sub", userId.toString());
            }
            if (deviceId != null) {
                ctx.put("deviceId", deviceId);
            }
            if (clientId != null) {
                ctx.put("clientId", clientId);
            }
            securityAuditService.logEvent(type, AuditCategory.AUTHENTICATION, ctx);
        } catch (Exception ex) {
            // Fail-safe: NFR-4 audit row must never break the transition.
            LOGGER.warn("[Session audit] Failed to emit {} for sid={} user={} reason={}: {}",
                    type, sid, userId, reason, ex.getMessage());
        }
    }
}
