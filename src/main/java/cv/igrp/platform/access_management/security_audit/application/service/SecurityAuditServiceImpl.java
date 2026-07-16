package cv.igrp.platform.access_management.security_audit.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import cv.igrp.platform.access_management.security_audit.domain.entities.SecurityAuditLogEntity;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditCategory;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditEventType;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditStatus;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsArea;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsEntityType;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsOperation;
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
 *
 * <p><strong>Every public method must carry {@code @Transactional(REQUIRES_NEW)}.</strong>
 * {@link SecurityAuditChainService#append} is {@code MANDATORY}, so it needs a
 * transaction already open. The convenience methods reach the chain by calling
 * {@link #logEvent} on {@code this}, which does <em>not</em> pass through the
 * Spring AOP proxy — so an annotation on {@code logEvent} alone never applies and
 * every event is silently dropped by the fail-safe catch (R1.2). Annotating each
 * entry point makes the proxy start the transaction on the way in; the inner
 * self-invocation then simply joins it.
 * {@code SecurityAuditServiceTransactionBoundaryTest} guards this.
 */
@Service
public class SecurityAuditServiceImpl implements SecurityAuditService {

    private static final Logger logger = LoggerFactory.getLogger(SecurityAuditServiceImpl.class);
    private static final String DEFAULT_DECISION_REASON = "No explicit decision reason provided";

    private final SecurityAuditLogRepository auditLogRepository;
    private final SecurityAuditContextProvider contextProvider;
    private final SecurityAuditChainService chainService;
    private final ObjectMapper objectMapper;

    public SecurityAuditServiceImpl(SecurityAuditLogRepository auditLogRepository,
                                    SecurityAuditContextProvider contextProvider,
                                    SecurityAuditChainService chainService,
                                    ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.contextProvider = contextProvider;
        this.chainService = chainService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(AuditEventType type, AuditCategory category, Map<String, Object> context) {
        try {
            SecurityAuditLogEntity logEntity = buildBaseEntity(type, category, context);

            // Append through the hash chain (advisory-locked, tamper-evident)
            // rather than a bare save, so previous_hash/current_hash/sequence
            // are set atomically in this REQUIRES_NEW transaction.
            chainService.append(logEntity);

            logger.info("[Security audit] Event: {}, Category: {}, User: {}, Session: {}, Context: {}",
                    type, category, logEntity.getUserId(), logEntity.getSessionId(), logEntity.getContextData());

        } catch (Exception e) {
            logger.error("[Security audit] Failed to save security audit log. Event: {}, Category: {}", type, category, e);
            // Fail-safe: Do not rethrow the exception
        }
    }

    /**
     * Builds and populates a {@link SecurityAuditLogEntity} from the ambient
     * request context merged with the caller-supplied {@code context}. Does not
     * persist — callers append it through the chain service.
     */
    private SecurityAuditLogEntity buildBaseEntity(AuditEventType type, AuditCategory category,
                                                   Map<String, Object> context) {
        Map<String, Object> fullContext = new HashMap<>(contextProvider.getContext());
        fullContext.putAll(context);

        SecurityAuditLogEntity logEntity = new SecurityAuditLogEntity();
        logEntity.setEventType(type);
        logEntity.setCategory(category);
        logEntity.setTimestamp(LocalDateTime.now());

        logEntity.setUserId(asString(fullContext.get("userId")));
        logEntity.setUsername(asString(fullContext.get("username")));
        logEntity.setSessionId(asString(fullContext.get("sessionId")));
        logEntity.setIpAddress(asString(fullContext.get("ipAddress")));
        logEntity.setUserAgent(asString(fullContext.get("userAgent")));
        logEntity.setCorrelationId(asString(fullContext.get("correlationId")));
        logEntity.setRequestPath(asString(fullContext.get("requestPath")));
        logEntity.setDecisionReason(resolveDecisionReason(category, fullContext));

        try {
            logEntity.setContextData(objectMapper.writeValueAsString(fullContext));
        } catch (Exception e) {
            logger.error("[Security audit] Failed to serialize audit context to JSON", e);
            logEntity.setContextData("{\"error\":\"Failed to serialize context\"}");
        }
        return logEntity;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAuthenticationSuccess() {
        logEvent(AuditEventType.LOGIN_SUCCESS, AuditCategory.AUTHENTICATION, Map.of());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAuthenticationFailure(String reason) {
        logEvent(AuditEventType.LOGIN_FAILURE, AuditCategory.AUTHENTICATION, Map.of("reason", reason));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logProfileSwitch(Integer oldRole, Integer newRole) {
        Map<String, Object> context = new HashMap<>();
        context.put("oldRole", oldRole);
        context.put("newRole", newRole);
        logEvent(AuditEventType.PROFILE_ACTIVATED, AuditCategory.PRIVILEGE, context);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAccessDenied(String permission) {
        logAccessDenied(permission, DEFAULT_DECISION_REASON);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAccessDenied(String permission, String reason) {
        String resolvedReason = (reason == null || reason.isBlank()) ? DEFAULT_DECISION_REASON : reason;
        logEvent(AuditEventType.ACCESS_DENIED, AuditCategory.AUTHORIZATION, Map.of(
                "permission", permission,
                "decisionReason", resolvedReason
        ));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logUserChange(String targetUserId, String operation) {
        AuditEventType eventType = switch (operation.toUpperCase()) {
            case "CREATE" -> AuditEventType.USER_CREATED;
            case "UPDATE" -> AuditEventType.USER_UPDATED;
            case "INACTIVE" -> AuditEventType.USER_INACTIVATED;
            case "ACTIVE" -> AuditEventType.USER_ACTIVATED;
            default -> throw new IllegalArgumentException("Invalid user operation for auditing: " + operation);
        };
        logEvent(eventType, AuditCategory.USER_MANAGEMENT, Map.of("targetUserId", targetUserId));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSettingsEvent(SettingsArea area, SettingsEntityType entityType, SettingsOperation operation,
                                 String entityName, String relatedEntity, String previousValue, String newValue) {
        logSettingsEvent(area, entityType, operation, entityName, relatedEntity, previousValue, newValue,
                AuditStatus.SUCCESS);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSettingsEvent(SettingsArea area, SettingsEntityType entityType, SettingsOperation operation,
                                 String entityName, String relatedEntity, String previousValue, String newValue,
                                 AuditStatus status) {
        try {
            SecurityAuditLogEntity logEntity = buildBaseEntity(
                    AuditEventType.SYSTEM_CONFIGURATION_CHANGED, AuditCategory.SYSTEM, Map.of());
            // N7: new settings rows carry their data in the typed columns below,
            // not the deprecated contextData JSON blob.
            logEntity.setContextData(null);
            logEntity.setSettingsArea(area);
            logEntity.setSettingsEntityType(entityType);
            logEntity.setSettingsOperation(operation);
            logEntity.setEntityName(entityName);
            logEntity.setRelatedEntity(relatedEntity);
            logEntity.setPreviousValue(previousValue);
            logEntity.setNewValue(newValue);
            logEntity.setStatus(status != null ? status : AuditStatus.SUCCESS);

            chainService.append(logEntity);

            logger.info("[Security audit] Settings event: area={}, entityType={}, operation={}, entity={}, related={}, status={}",
                    area, entityType, operation, entityName, relatedEntity, logEntity.getStatus());

        } catch (Exception e) {
            logger.error("[Security audit] Failed to save settings audit log. Area: {}, Operation: {}, Entity: {}",
                    area, operation, entityName, e);
            // Fail-safe: Do not rethrow the exception (N4)
        }
    }

    private String resolveDecisionReason(AuditCategory category, Map<String, Object> context) {
        if (category != AuditCategory.AUTHORIZATION) {
            return null;
        }

        Object decisionReason = context.get("decisionReason");
        if (decisionReason != null && !decisionReason.toString().isBlank()) {
            return decisionReason.toString();
        }

        Object reason = context.get("reason");
        if (reason != null && !reason.toString().isBlank()) {
            return reason.toString();
        }

        return DEFAULT_DECISION_REASON;
    }

    private String asString(Object value) {
        return value != null ? value.toString() : null;
    }
}