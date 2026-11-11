package cv.igrp.platform.access_management.security_audit.application.service;

import cv.igrp.platform.access_management.shared.infrastructure.json.PersistedJson;
import cv.igrp.platform.access_management.security_audit.domain.entities.SecurityAuditLogEntity;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditCategory;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditEventType;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditStatus;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsArea;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsEntityType;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsOperation;
import cv.igrp.platform.access_management.security_audit.infrastructure.persistence.SecurityAuditLogRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of the {@link SecurityAuditService}.
 * This service is responsible for creating and persisting security audit log entries.
 *
 * <p>Two write paths (v3-23):
 * <ul>
 *   <li><b>Access events</b> (logins, denials, token and session events) run in
 *       their own {@code REQUIRES_NEW} transaction, so they survive a rollback of
 *       the request that produced them. A failed write is logged and not
 *       rethrown, so the access decision itself still stands.</li>
 *   <li><b>Settings events</b> join the caller's transaction and rethrow on
 *       failure, so a change is never committed without its audit row.</li>
 * </ul>
 *
 * <p><strong>Every public access-event method must carry {@code @Transactional(REQUIRES_NEW)}.</strong>
 * {@link SecurityAuditChainService#append} is {@code MANDATORY}, so it needs a
 * transaction already open. A call on {@code this} does <em>not</em> pass through
 * the Spring AOP proxy, so an annotation reached only that way never applies and
 * every event would be silently dropped by the fail-safe catch (R1.2). Each entry
 * point therefore opens the transaction through the proxy on the way in, then
 * writes through the private, unannotated {@code writeAccessEvent}; no
 * transactional method is ever called on {@code this}.
 * {@code SecurityAuditServiceTransactionBoundaryTest} guards this.
 */
@Service
public class SecurityAuditServiceImpl implements SecurityAuditService {

    private static final Logger logger = LoggerFactory.getLogger(SecurityAuditServiceImpl.class);
    private static final String DEFAULT_DECISION_REASON = "No explicit decision reason provided";

    /** Counts failed audit writes, tagged {@code path=access|settings}, so a lost row is never silent. */
    static final String WRITE_FAILURES_METRIC = "igrp.audit.write.failures";

    private final SecurityAuditLogRepository auditLogRepository;
    private final SecurityAuditContextProvider contextProvider;
    private final SecurityAuditChainService chainService;
    private final Counter accessWriteFailures;
    private final Counter settingsWriteFailures;

    public SecurityAuditServiceImpl(SecurityAuditLogRepository auditLogRepository,
                                    SecurityAuditContextProvider contextProvider,
                                    SecurityAuditChainService chainService,
                                    MeterRegistry meterRegistry) {
        this.auditLogRepository = auditLogRepository;
        this.contextProvider = contextProvider;
        this.chainService = chainService;
        this.accessWriteFailures = writeFailures(meterRegistry, "access");
        this.settingsWriteFailures = writeFailures(meterRegistry, "settings");
    }

    private static Counter writeFailures(MeterRegistry meterRegistry, String path) {
        return Counter.builder(WRITE_FAILURES_METRIC)
                .description("Audit rows that failed to be written")
                .tag("path", path)
                .register(meterRegistry);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(AuditEventType type, AuditCategory category, Map<String, Object> context) {
        writeAccessEvent(type, category, context, AuditReportContext.empty());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(AuditEventType type, AuditCategory category, Map<String, Object> context,
                         AuditReportContext report) {
        writeAccessEvent(type, category, context, report);
    }

    /**
     * Writes one access event in the transaction the calling entry point opened.
     * Deliberately not {@code @Transactional}: it is only ever called on
     * {@code this}, where the annotation would never apply.
     */
    private void writeAccessEvent(AuditEventType type, AuditCategory category, Map<String, Object> context,
                                  AuditReportContext report) {
        try {
            SecurityAuditLogEntity logEntity = buildBaseEntity(type, category, context, report);

            // Append through the hash chain (advisory-locked, tamper-evident)
            // rather than a bare save, so previous_hash/current_hash/sequence
            // are set atomically in this REQUIRES_NEW transaction.
            chainService.append(logEntity);

            logger.info("[Security audit] Event: {}, Category: {}, User: {}, Session: {}, Context: {}",
                    type, category, logEntity.getUserId(), logEntity.getSessionId(), logEntity.getContextData());

        } catch (Exception e) {
            accessWriteFailures.increment();
            logger.error("[Security audit] Failed to save security audit log. Event: {}, Category: {}", type, category, e);
            // Not rethrown: the access decision stands even when its record fails.
            // A failed insert leaves this REQUIRES_NEW transaction unusable, so roll
            // it back quietly here rather than let the commit throw at the caller.
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            }
        }
    }

    /**
     * Builds and populates a {@link SecurityAuditLogEntity} from the ambient
     * request context merged with the caller-supplied {@code context}. Does not
     * persist — callers append it through the chain service.
     */
    private SecurityAuditLogEntity buildBaseEntity(AuditEventType type, AuditCategory category,
                                                   Map<String, Object> context, AuditReportContext report) {
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
            logEntity.setContextData(PersistedJson.MAPPER.writeValueAsString(fullContext));
        } catch (Exception e) {
            logger.error("[Security audit] Failed to serialize audit context to JSON", e);
            logEntity.setContextData("{\"error\":\"Failed to serialize context\"}");
        }

        applyReportColumns(logEntity, type, fullContext, report);
        return logEntity;
    }

    /**
     * Populates the typed columns behind the Audit and Access reports.
     *
     * <p>Caller-supplied values on {@code report} always win. Anything left unset
     * is derived from the event and the ambient request context, because these
     * columns are read by human-facing reports: leaving them null (as every
     * auth/authorization row did before) makes the reports structurally hollow and
     * their filters unable to match anything (R1.5.1 / R1.5.2).
     *
     * <p>{@code operationState}, {@code authorizedBy} and the period bounds have no
     * honest derivation from an authentication event, so they stay null unless a
     * caller supplies them through {@link AuditReportContext}.
     */
    private void applyReportColumns(SecurityAuditLogEntity entity, AuditEventType type,
                                    Map<String, Object> context, AuditReportContext report) {
        entity.setStatus(report.status() != null ? report.status() : deriveStatus(type));
        entity.setAction(hasText(report.action()) ? report.action() : deriveAction(type));
        entity.setAccessRole(hasText(report.accessRole())
                ? report.accessRole() : asString(context.get("accessRole")));
        entity.setApplicationModule(hasText(report.module())
                ? report.module() : deriveModule(asString(context.get("requestPath"))));
        entity.setOperationState(report.operationState());
        entity.setAuthorizedBy(report.authorizedBy());
        entity.setPeriodStart(report.periodStart());
        entity.setPeriodEnd(report.periodEnd());
    }

    /**
     * Outcome implied by the event type. Only the two states an event can be
     * classified into on its own are derived here; {@code UNUSUAL_IP} and
     * {@code PENDING} require knowledge the event does not carry, so a caller must
     * pass those explicitly.
     */
    private static AuditStatus deriveStatus(AuditEventType type) {
        return switch (type) {
            case ACCESS_DENIED, LOGIN_FAILURE, TOKEN_REJECTED, TOKEN_EXPIRED,
                 SESSION_LIMIT_EXCEEDED, ACCOUNT_LOCKED -> AuditStatus.ACCESS_DENIED;
            default -> AuditStatus.SUCCESS;
        };
    }

    /** Human-readable action label for the report, e.g. LOGIN_SUCCESS → "Login Success". */
    private static String deriveAction(AuditEventType type) {
        String[] words = type.name().split("_");
        StringBuilder label = new StringBuilder(type.name().length());
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(word.charAt(0)).append(word.substring(1).toLowerCase());
        }
        return label.toString();
    }

    /**
     * The API area the request touched, taken as the segment after {@code /api}
     * (e.g. {@code /igrp-access-management/api/auth/audit} → {@code auth}). Tolerates
     * a deployment context path. Null when there is no request or no {@code /api}
     * segment — for example an audit write from a scheduler.
     */
    private static String deriveModule(String requestPath) {
        if (!hasText(requestPath)) {
            return null;
        }
        String[] segments = requestPath.split("/");
        for (int i = 0; i < segments.length - 1; i++) {
            if ("api".equals(segments[i]) && hasText(segments[i + 1])) {
                return segments[i + 1];
            }
        }
        return null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAuthenticationSuccess() {
        writeAccessEvent(AuditEventType.LOGIN_SUCCESS, AuditCategory.AUTHENTICATION, Map.of(), AuditReportContext.empty());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAuthenticationFailure(String reason) {
        writeAccessEvent(AuditEventType.LOGIN_FAILURE, AuditCategory.AUTHENTICATION, Map.of("reason", reason), AuditReportContext.empty());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logProfileSwitch(Integer oldRole, Integer newRole) {
        Map<String, Object> context = new HashMap<>();
        context.put("oldRole", oldRole);
        context.put("newRole", newRole);
        writeAccessEvent(AuditEventType.PROFILE_ACTIVATED, AuditCategory.PRIVILEGE, context, AuditReportContext.empty());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAccessDenied(String permission) {
        writeAccessDenied(permission, DEFAULT_DECISION_REASON);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAccessDenied(String permission, String reason) {
        writeAccessDenied(permission, reason);
    }

    private void writeAccessDenied(String permission, String reason) {
        String resolvedReason = (reason == null || reason.isBlank()) ? DEFAULT_DECISION_REASON : reason;
        writeAccessEvent(AuditEventType.ACCESS_DENIED, AuditCategory.AUTHORIZATION, Map.of(
                "permission", permission,
                "decisionReason", resolvedReason
        ), AuditReportContext.empty());
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
        writeAccessEvent(eventType, AuditCategory.USER_MANAGEMENT, Map.of("targetUserId", targetUserId), AuditReportContext.empty());
    }

    @Override
    @Transactional
    public void logSettingsEvent(SettingsArea area, SettingsEntityType entityType, SettingsOperation operation,
                                 String entityName, String relatedEntity, String previousValue, String newValue) {
        logSettingsEvent(area, entityType, operation, entityName, relatedEntity, previousValue, newValue,
                AuditStatus.SUCCESS);
    }

    /**
     * Joins the caller's transaction (v3-23): the row commits with the change it
     * records. A failure is logged and rethrown, so the change rolls back rather
     * than going unrecorded.
     *
     * <p>The row is built now but appended just before the transaction commits.
     * Appending takes the chain's advisory lock, which is held until commit. If it
     * were taken here, an access event written later in the same request (in its
     * own {@code REQUIRES_NEW} transaction, e.g. the session revocation that
     * follows a role change) would wait for a lock its own thread holds, and
     * never return.
     */
    @Override
    @Transactional
    public void logSettingsEvent(SettingsArea area, SettingsEntityType entityType, SettingsOperation operation,
                                 String entityName, String relatedEntity, String previousValue, String newValue,
                                 AuditStatus status) {
        SecurityAuditLogEntity logEntity;
        try {
            logEntity = buildBaseEntity(
                    AuditEventType.SYSTEM_CONFIGURATION_CHANGED, AuditCategory.SYSTEM, Map.of(),
                    AuditReportContext.builder().status(status).build());
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
        } catch (RuntimeException e) {
            throw countAndLogSettingsFailure(describeSettingsEvent(area, operation, entityName), e);
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    // The change's own writes go first and fail as themselves,
                    // not as an audit failure.
                    chainService.flushPendingChanges();
                    appendSettingsRow(logEntity);
                }
            });
        } else {
            appendSettingsRow(logEntity);
        }
    }

    /** Appends a settings row; a failure is counted, logged and rethrown so the change rolls back. */
    private void appendSettingsRow(SecurityAuditLogEntity logEntity) {
        try {
            chainService.append(logEntity);
            logger.info("[Security audit] Settings event: area={}, entityType={}, operation={}, entity={}, related={}, status={}",
                    logEntity.getSettingsArea(), logEntity.getSettingsEntityType(), logEntity.getSettingsOperation(),
                    logEntity.getEntityName(), logEntity.getRelatedEntity(), logEntity.getStatus());
        } catch (RuntimeException e) {
            throw countAndLogSettingsFailure(describeSettingsEvent(logEntity.getSettingsArea(),
                    logEntity.getSettingsOperation(), logEntity.getEntityName()), e);
        }
    }

    /** Counts and logs a failed settings write, and returns the exception for the caller to rethrow. */
    private RuntimeException countAndLogSettingsFailure(String description, RuntimeException e) {
        settingsWriteFailures.increment();
        logger.error("[Security audit] Failed to save settings audit log. {}", description, e);
        return e;
    }

    private static String describeSettingsEvent(SettingsArea area, SettingsOperation operation, String entityName) {
        return "Area: " + area + ", Operation: " + operation + ", Entity: " + entityName;
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