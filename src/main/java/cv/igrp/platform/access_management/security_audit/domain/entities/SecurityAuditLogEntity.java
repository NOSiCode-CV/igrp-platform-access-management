package cv.igrp.platform.access_management.security_audit.domain.entities;

import cv.igrp.platform.access_management.security_audit.domain.enums.AuditCategory;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditEventType;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditStatus;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsArea;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsEntityType;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsOperation;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a security audit log entry in the database.
 * It captures critical information about security-relevant events, such as who did what, from where, and when.
 * This entity is crucial for security monitoring, incident response, and compliance with standards like ASVS.
 */
@Entity
@Table(name = "t_security_audit_log", indexes = {
    @Index(name = "idx_audit_log_user_id", columnList = "userId"),
    @Index(name = "idx_audit_log_event_type", columnList = "eventType"),
    @Index(name = "idx_audit_log_timestamp", columnList = "timestamp")
})
public class SecurityAuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    /**
     * Contiguous chain position. Assigned by {@code SecurityAuditChainService}
     * inside the advisory-lock critical section (0 is the GENESIS anchor).
     */
    @Column(name = "sequence_number", unique = true)
    private Long sequenceNumber;

    /** {@code current_hash} of the preceding chain row (GENESIS anchor for the first real row). */
    @Column(name = "previous_hash", length = 64)
    private String previousHash;

    /** HMAC-SHA256(secret, previous_hash || serialized row fields). */
    @Column(name = "current_hash", length = 64)
    private String currentHash;

    /** Wall-clock at append time, epoch millis — part of the hash input. */
    @Column(name = "epoch_ms")
    private Long epochMs;

    /** HMAC-SHA256 of the raw IP; participates in the hash so editing ip_address breaks the chain. */
    @Column(name = "ip_hash", length = 64)
    private String ipHash;

    private String userId;
    private String username;
    private String sessionId;
    private String ipAddress;
    private String userAgent;
    private String correlationId;
    private String requestPath;
    private String decisionReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditCategory category;

    @Column(length = 2000)
    private String contextData; // JSON format for additional details

    @Column(nullable = false)
    private LocalDateTime timestamp;

    // --- Report columns (Phase 2 of Unified Audit & Reports) ---------------
    // Typed columns backing the Audit / Access / Settings reports. Populated by
    // Phase 3 instrumentation; Phase 1 auth rows leave them null. All of these
    // (except the reserved, read-derived `device`) participate in the hash chain
    // via SecurityAuditChainService#serialize — extending the input, which is why
    // Phase 2 requires a rehash-on-boot pass in dev/staging (R2.2 / V11_1).

    /** Audit + Access report: accessed application module. */
    @Column(name = "application_module", length = 100)
    private String applicationModule;

    /** Audit + Access report: role in effect for the action. */
    @Column(name = "access_role", length = 255)
    private String accessRole;

    /** Audit report: free-text operation state (e.g. "Completed"). */
    @Column(name = "operation_state", length = 50)
    private String operationState;

    /** Reserved column; the report {@code device} is derived at read time from
     *  {@code user_agent} via {@code UserAgentParser} and never persisted here. */
    @Column(name = "device", length = 255)
    private String device;

    /** Audit report: who authorized the operation. */
    @Column(name = "authorized_by", length = 255)
    private String authorizedBy;

    /** Outcome status shown on every report. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private AuditStatus status;

    /** Access report: human-readable action label. */
    @Column(name = "action", length = 100)
    private String action;

    /** Settings report: administrative area. */
    @Enumerated(EnumType.STRING)
    @Column(name = "settings_area", length = 20)
    private SettingsArea settingsArea;

    /** Settings report: target entity type. */
    @Enumerated(EnumType.STRING)
    @Column(name = "settings_entity_type", length = 30)
    private SettingsEntityType settingsEntityType;

    /** Settings report: operation performed. */
    @Enumerated(EnumType.STRING)
    @Column(name = "settings_operation", length = 30)
    private SettingsOperation settingsOperation;

    /** Settings report: name of the target entity. */
    @Column(name = "entity_name", length = 500)
    private String entityName;

    /** Settings report: related entity for associations/assignments (nullable). */
    @Column(name = "related_entity", length = 500)
    private String relatedEntity;

    /** Settings report: previous value on EDIT (nullable). */
    @Column(name = "previous_value", columnDefinition = "TEXT")
    private String previousValue;

    /** Settings report: new value on EDIT (nullable). */
    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    /** Audit report: period start (distinct from the record {@code timestamp}). */
    @Column(name = "period_start")
    private Instant periodStart;

    /** Audit report: period end. */
    @Column(name = "period_end")
    private Instant periodEnd;

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Long getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(Long sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public void setPreviousHash(String previousHash) {
        this.previousHash = previousHash;
    }

    public String getCurrentHash() {
        return currentHash;
    }

    public void setCurrentHash(String currentHash) {
        this.currentHash = currentHash;
    }

    public Long getEpochMs() {
        return epochMs;
    }

    public void setEpochMs(Long epochMs) {
        this.epochMs = epochMs;
    }

    public String getIpHash() {
        return ipHash;
    }

    public void setIpHash(String ipHash) {
        this.ipHash = ipHash;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public void setRequestPath(String requestPath) {
        this.requestPath = requestPath;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public void setDecisionReason(String decisionReason) {
        this.decisionReason = decisionReason;
    }

    public AuditEventType getEventType() {
        return eventType;
    }

    public void setEventType(AuditEventType eventType) {
        this.eventType = eventType;
    }

    public AuditCategory getCategory() {
        return category;
    }

    public void setCategory(AuditCategory category) {
        this.category = category;
    }

    public String getContextData() {
        return contextData;
    }

    public void setContextData(String contextData) {
        this.contextData = contextData;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getApplicationModule() {
        return applicationModule;
    }

    public void setApplicationModule(String applicationModule) {
        this.applicationModule = applicationModule;
    }

    public String getAccessRole() {
        return accessRole;
    }

    public void setAccessRole(String accessRole) {
        this.accessRole = accessRole;
    }

    public String getOperationState() {
        return operationState;
    }

    public void setOperationState(String operationState) {
        this.operationState = operationState;
    }

    public String getDevice() {
        return device;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public String getAuthorizedBy() {
        return authorizedBy;
    }

    public void setAuthorizedBy(String authorizedBy) {
        this.authorizedBy = authorizedBy;
    }

    public AuditStatus getStatus() {
        return status;
    }

    public void setStatus(AuditStatus status) {
        this.status = status;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public SettingsArea getSettingsArea() {
        return settingsArea;
    }

    public void setSettingsArea(SettingsArea settingsArea) {
        this.settingsArea = settingsArea;
    }

    public SettingsEntityType getSettingsEntityType() {
        return settingsEntityType;
    }

    public void setSettingsEntityType(SettingsEntityType settingsEntityType) {
        this.settingsEntityType = settingsEntityType;
    }

    public SettingsOperation getSettingsOperation() {
        return settingsOperation;
    }

    public void setSettingsOperation(SettingsOperation settingsOperation) {
        this.settingsOperation = settingsOperation;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public String getRelatedEntity() {
        return relatedEntity;
    }

    public void setRelatedEntity(String relatedEntity) {
        this.relatedEntity = relatedEntity;
    }

    public String getPreviousValue() {
        return previousValue;
    }

    public void setPreviousValue(String previousValue) {
        this.previousValue = previousValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }

    public Instant getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(Instant periodStart) {
        this.periodStart = periodStart;
    }

    public Instant getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(Instant periodEnd) {
        this.periodEnd = periodEnd;
    }
}