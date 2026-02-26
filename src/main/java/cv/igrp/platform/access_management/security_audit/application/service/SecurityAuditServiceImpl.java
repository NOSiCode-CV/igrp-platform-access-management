package cv.igrp.platform.access_management.security_audit.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import cv.igrp.platform.access_management.security_audit.domain.entities.SecurityAuditLogEntity;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditCategory;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditEventType;
import cv.igrp.platform.access_management.security_audit.infrastructure.persistence.SecurityAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of the {@link SecurityAuditService}.
 * This service is responsible for creating and persisting security audit log entries.
 * It is designed to be fail-safe, ensuring that audit failures do not disrupt business logic.
 */
@Service
public class SecurityAuditServiceImpl implements SecurityAuditService {

    private static final Logger logger = LoggerFactory.getLogger(SecurityAuditServiceImpl.class);

    private final SecurityAuditLogRepository auditLogRepository;
    private final SecurityAuditContextProvider contextProvider;
    private final ObjectMapper objectMapper;

    public SecurityAuditServiceImpl(SecurityAuditLogRepository auditLogRepository,
                                    SecurityAuditContextProvider contextProvider,
                                    ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.contextProvider = contextProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(AuditEventType type, AuditCategory category, Map<String, Object> context) {
        try {
            Map<String, Object> fullContext = new HashMap<>(contextProvider.getContext());
            fullContext.putAll(context);

            SecurityAuditLogEntity logEntity = new SecurityAuditLogEntity();
            logEntity.setEventType(type);
            logEntity.setCategory(category);
            logEntity.setTimestamp(LocalDateTime.now());

            logEntity.setUserId((String) fullContext.get("userId"));
            logEntity.setUsername((String) fullContext.get("username"));
            logEntity.setSessionId((String) fullContext.get("sessionId"));
            logEntity.setIpAddress((String) fullContext.get("ipAddress"));
            logEntity.setUserAgent((String) fullContext.get("userAgent"));

            try {
                logEntity.setContextData(objectMapper.writeValueAsString(fullContext));
            } catch (Exception e) {
                logger.error("[Security audit] Failed to serialize audit context to JSON", e);
                logEntity.setContextData("{\"error\":\"Failed to serialize context\"}");
            }

            auditLogRepository.save(logEntity);

            logger.info("[Security audit] Event: {}, Category: {}, User: {}, Session: {}, Context: {}",
                    type, category, logEntity.getUserId(), logEntity.getSessionId(), logEntity.getContextData());

        } catch (Exception e) {
            logger.error("[Security audit] Failed to save security audit log. Event: {}, Category: {}", type, category, e);
            // Fail-safe: Do not rethrow the exception
        }
    }

    @Override
    public void logAuthenticationSuccess() {
        logEvent(AuditEventType.LOGIN_SUCCESS, AuditCategory.AUTHENTICATION, Map.of());
    }

    @Override
    public void logAuthenticationFailure(String reason) {
        logEvent(AuditEventType.LOGIN_FAILURE, AuditCategory.AUTHENTICATION, Map.of("reason", reason));
    }

    @Override
    public void logProfileSwitch(Integer oldRole, Integer newRole) {
        Map<String, Object> context = new HashMap<>();
        context.put("oldRole", oldRole);
        context.put("newRole", newRole);
        logEvent(AuditEventType.PROFILE_ACTIVATED, AuditCategory.PRIVILEGE, context);
    }

    @Override
    public void logAccessDenied(String permission) {
        logEvent(AuditEventType.ACCESS_DENIED, AuditCategory.AUTHORIZATION, Map.of("permission", permission));
    }

    @Override
    public void logUserChange(String targetUserId, String operation) {
        AuditEventType eventType = switch (operation.toUpperCase()) {
            case "CREATE" -> AuditEventType.USER_CREATED;
            case "INACTIVE" -> AuditEventType.USER_INACTIVATED;
            case "ACTIVE" -> AuditEventType.USER_ACTIVATED;
            default -> throw new IllegalArgumentException("Invalid user operation for auditing: " + operation);
        };
        logEvent(eventType, AuditCategory.USER_MANAGEMENT, Map.of("targetUserId", targetUserId));
    }
}