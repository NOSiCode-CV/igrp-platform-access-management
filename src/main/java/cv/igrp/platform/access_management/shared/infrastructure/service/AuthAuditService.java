package cv.igrp.platform.access_management.shared.infrastructure.service;

import cv.igrp.platform.access_management.shared.domain.audit.AuthAuditContext;
import cv.igrp.platform.access_management.shared.domain.audit.AuthAuditLog;
import cv.igrp.platform.access_management.shared.domain.audit.AuthEventType;
import cv.igrp.platform.access_management.shared.domain.audit.IdentifierType;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.AuthAuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class AuthAuditService {

    private static final Logger log = LoggerFactory.getLogger(AuthAuditService.class);
    private final AuthAuditLogRepository repository;

    public AuthAuditService(AuthAuditLogRepository repository) {
        this.repository = repository;
    }

    @Async
    public void logSuccess(AuthAuditContext ctx) {
        logEvent(AuthEventType.LOGIN_SUCCESS, ctx, null);
    }

    @Async
    public void logFailure(AuthAuditContext ctx, String reason) {
        logEvent(AuthEventType.LOGIN_FAILURE, ctx, reason);
    }

    @Async
    public void logEvent(AuthEventType type, AuthAuditContext ctx) {
        logEvent(type, ctx, null);
    }

    private void logEvent(AuthEventType type, AuthAuditContext ctx, String reason) {
        try {
            String ipAddress = null;
            String userAgent = null;
            if (ctx.request() != null) {
                ipAddress = ctx.request().getRemoteAddr();
                userAgent = ctx.request().getHeader("User-Agent");
            }

            AuthAuditLog auditLog = AuthAuditLog.builder()
                    .eventType(type)
                    .identifierType(ctx.identifierType() != null ? ctx.identifierType() : IdentifierType.UNKNOWN)
                    .identifierValue(hash(ctx.identifierValue()))
                    .userId(ctx.userId())
                    .applicationCode(ctx.applicationCode())
                    .sessionId(ctx.sessionId())
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .failureReason(reason)
                    .build();

            repository.save(auditLog);
        } catch (Exception e) {
            log.error("[AUDIT] Failed to save authentication audit log", e);
        }
    }

    public static AuthAuditContext fromAutentikaJwt(Jwt jwt, HttpServletRequest request) {
        IdentifierType identifierType = IdentifierType.UNKNOWN;
        String identifierValue = null;

        java.util.List<String> amr = jwt.hasClaim("amr") ? jwt.getClaimAsStringList("amr") : null;
        String acr = jwt.hasClaim("acr") ? jwt.getClaimAsString("acr") : null;

        if (amr != null && amr.contains("BasicAuthenticator") && "pwd".equalsIgnoreCase(acr)) {
            identifierType = IdentifierType.EMAIL;
            identifierValue = jwt.hasClaim("email") ? jwt.getClaimAsString("email") : null;
        } else if (amr != null && amr.contains("OpenIDConnectAuthenticator")) {
            if ("cmdcv".equalsIgnoreCase(acr)) {
                identifierType = IdentifierType.PHONE;
                identifierValue = jwt.hasClaim("phone_number") ? jwt.getClaimAsString("phone_number") : null;
            } else if ("cni".equalsIgnoreCase(acr)) {
                identifierType = IdentifierType.CNI;
                identifierValue = jwt.getSubject();
            }
        } else if (amr == null || amr.isEmpty() || amr.contains("refresh_token")) {
            if (jwt.hasClaim("phone_number")) {
                identifierType = IdentifierType.PHONE;
                identifierValue = jwt.getClaimAsString("phone_number");
            } else if (jwt.hasClaim("email")) {
                identifierType = IdentifierType.EMAIL;
                identifierValue = jwt.getClaimAsString("email");
            }
        }

        String userId = jwt.getSubject();
        String sessionId = jwt.getId();
        String applicationCode = jwt.hasClaim("application_code") ? jwt.getClaimAsString("application_code") : null;

        return new AuthAuditContext(identifierType, identifierValue, userId, applicationCode, sessionId, request);
    }

    public static String hash(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            log.error("[AUDIT] Hashing algorithm not found", e);
            return null;
        }
    }
}
