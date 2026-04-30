package cv.igrp.platform.access_management.shared.domain.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Reference implementation for AuthAuditLog.
 *
 * Uses a hand-written Builder — do NOT replace with Lombok.
 * Lombok @Builder + JPA causes constructor conflicts that are hard to diagnose.
 * This pattern is guaranteed to compile and work with JPA and Spring Data.
 */
@Entity
@Table(name = "t_auth_audit_log", indexes = {
    @Index(name = "idx_audit_timestamp",        columnList = "timestamp"),
    @Index(name = "idx_audit_user_timestamp",   columnList = "user_id,timestamp"),
    @Index(name = "idx_audit_identifier_event", columnList = "identifier_value,event_type")
})
public class AuthAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private AuthEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "identifier_type", nullable = false, length = 20)
    private IdentifierType identifierType;

    /** SHA-256 hash of NIC or phone_number. Never the raw value. */
    @Column(name = "identifier_value", length = 64)
    private String identifierValue;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "application_code")
    private String applicationCode;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    /** JWT jti — correlates events from the same session. */
    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(name = "environment", length = 50)
    private String environment;

    /** Required by JPA — must be protected, not public. */
    protected AuthAuditLog() {}

    /** Private — only the Builder may call this. */
    private AuthAuditLog(Builder b) {
        this.eventType       = b.eventType;
        this.identifierType  = b.identifierType;
        this.identifierValue = b.identifierValue;
        this.userId          = b.userId;
        this.applicationCode = b.applicationCode;
        this.ipAddress       = b.ipAddress;
        this.userAgent       = b.userAgent;
        this.sessionId       = b.sessionId;
        this.failureReason   = b.failureReason;
        this.timestamp       = Instant.now();
        this.environment     = b.environment;
    }

    public static Builder builder() { return new Builder(); }

    // Getters only — no setters (immutable after construction)
    public UUID getId()                      { return id; }
    public AuthEventType getEventType()      { return eventType; }
    public IdentifierType getIdentifierType(){ return identifierType; }
    public String getIdentifierValue()       { return identifierValue; }
    public String getUserId()                { return userId; }
    public String getApplicationCode()       { return applicationCode; }
    public String getIpAddress()             { return ipAddress; }
    public String getUserAgent()             { return userAgent; }
    public String getSessionId()             { return sessionId; }
    public String getFailureReason()         { return failureReason; }
    public Instant getTimestamp()            { return timestamp; }
    public String getEnvironment()           { return environment; }

    public static class Builder {
        private AuthEventType eventType;
        private IdentifierType identifierType = IdentifierType.UNKNOWN;
        private String identifierValue, userId, applicationCode;
        private String ipAddress, userAgent, sessionId, failureReason, environment;

        public Builder eventType(AuthEventType v)       { this.eventType = v; return this; }
        public Builder identifierType(IdentifierType v) { this.identifierType = v; return this; }
        public Builder identifierValue(String v)        { this.identifierValue = v; return this; }
        public Builder userId(String v)                 { this.userId = v; return this; }
        public Builder applicationCode(String v)        { this.applicationCode = v; return this; }
        public Builder ipAddress(String v)              { this.ipAddress = v; return this; }
        public Builder userAgent(String v)              { this.userAgent = (v != null && v.length() > 512) ? v.substring(0, 512) : v; return this; }
        public Builder sessionId(String v)              { this.sessionId = v; return this; }
        public Builder failureReason(String v)          { this.failureReason = v; return this; }
        public Builder environment(String v)            { this.environment = v; return this; }

        public AuthAuditLog build() {
            if (eventType == null) throw new IllegalStateException("eventType is required");
            return new AuthAuditLog(this);
        }
    }
}
