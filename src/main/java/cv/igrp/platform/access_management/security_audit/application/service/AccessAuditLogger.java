package cv.igrp.platform.access_management.security_audit.application.service;

import cv.igrp.platform.access_management.security_audit.domain.enums.AuditCategory;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Audit rows for a user's access: their sign-out, and their status changing (first login,
 * invitation answered, an administrator deactivating or reactivating them). Calls never fail
 * the action they record: a failure to write the row is logged and swallowed.
 *
 * <p>Actors: {@link #SYSTEM} for jobs and listeners, {@link #USER} for the user acting on
 * themselves, {@link #adminActor(String)} for an administrator.
 */
@Component
public class AccessAuditLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccessAuditLogger.class);

    public static final String SYSTEM = "SYSTEM";
    public static final String USER = "USER";

    private static final String REASON = "reason";
    private static final String ACTOR = "actor";

    /** The canonical {@code ADMIN:{externalId}} actor. */
    public static String adminActor(String externalId) {
        return "ADMIN:" + (externalId == null ? "unknown" : externalId);
    }

    private final SecurityAuditService securityAuditService;

    public AccessAuditLogger(SecurityAuditService securityAuditService) {
        this.securityAuditService = securityAuditService;
    }

    /** RP-initiated logout through {@code /connect/logout} (v3-23). */
    public void recordLogout(String userId) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put(REASON, "USER_LOGOUT");
        ctx.put(ACTOR, USER);
        putUser(ctx, userId);
        log(AuditEventType.LOGOUT, AuditCategory.AUTHENTICATION, ctx, userId);
    }

    /**
     * A kill (#19, #33): the user's authorizations are revoked on the whole platform, recorded as
     * {@link AuditEventType#TOKEN_REVOKED} with the reason and who asked.
     */
    public void recordAccessEnded(String userId, String reason, String actor) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put(REASON, reason == null ? "UNSPECIFIED" : reason);
        ctx.put(ACTOR, actor == null ? SYSTEM : actor);
        putUser(ctx, userId);
        log(AuditEventType.TOKEN_REVOKED, AuditCategory.AUTHENTICATION, ctx, userId);
    }

    /**
     * One row for every user-status transition (FIRST_LOGIN, INVITATION_ACCEPTED,
     * INVITATION_REJECTED, ADMIN_DEACTIVATE, ADMIN_REACTIVATE), as {@link AuditEventType#USER_UPDATED}
     * with {@code from}, {@code to}, {@code reason} and {@code actor}.
     */
    public void recordUserStatusTransitioned(String userId, String fromStatus, String toStatus,
                                             String actor, String reason) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put(REASON, reason == null ? "USER_STATUS_TRANSITIONED" : reason);
        ctx.put(ACTOR, actor == null ? SYSTEM : actor);
        ctx.put("from", fromStatus == null ? "<none>" : fromStatus);
        ctx.put("to", toStatus == null ? "<none>" : toStatus);
        putUser(ctx, userId);
        log(AuditEventType.USER_UPDATED, AuditCategory.USER_MANAGEMENT, ctx, userId);
    }

    private static void putUser(Map<String, Object> ctx, String userId) {
        if (userId != null) {
            ctx.put("userId", userId);
            ctx.put("sub", userId);
        }
    }

    private void log(AuditEventType type, AuditCategory category, Map<String, Object> ctx, String userId) {
        try {
            securityAuditService.logEvent(type, category, ctx);
        } catch (RuntimeException ex) {
            LOGGER.warn("[Access audit] Failed to record {} for user={}: {}", type, userId, ex.getMessage());
        }
    }
}
