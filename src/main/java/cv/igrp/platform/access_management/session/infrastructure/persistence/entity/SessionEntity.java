package cv.igrp.platform.access_management.session.infrastructure.persistence.entity;

import cv.igrp.platform.access_management.shared.config.AuditEntity;
import cv.igrp.framework.stereotype.IgrpEntity;
import cv.igrp.platform.access_management.session.domain.constants.SessionStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.envers.Audited;

import java.time.Instant;
import java.util.UUID;

@Audited
@Getter
@Setter
@ToString(exclude = {"user"})
@IgrpEntity
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_user_session",
       indexes = {
           @Index(name = "ix_session_user_status", columnList = "user_id, status"),
           @Index(name = "ix_session_expires_active", columnList = "expires_at"),
           @Index(name = "ix_session_user_device", columnList = "user_id, device_id"),
           @Index(name = "ix_session_jti", columnList = "jti")
       },
       uniqueConstraints = {
           @UniqueConstraint(name = "ux_session_session_id", columnNames = {"session_id"})
       })
public class SessionEntity extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Long id;

    @Column(name = "session_id", nullable = false, unique = true)
    private UUID sessionId;

    @NotNull(message = "User id is mandatory")
    @Column(name = "user_id", nullable = false)
    private String userId;

    @NotNull(message = "Session status is mandatory")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SessionStatus status;

    @NotNull(message = "Started at is mandatory")
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @NotNull(message = "Last seen at is mandatory")
    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @NotNull(message = "Expires at is mandatory")
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "absolute_expires_at")
    private Instant absoluteExpiresAt;

    @Column(name = "jti", length = 64)
    private String jti;

    @Column(name = "client_id", length = 128)
    private String clientId;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "client_ip")
    private String clientIp;

    @Column(name = "user_agent_hash")
    private String userAgentHash;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "closed_reason")
    private String closedReason;

    @Column(name = "closed_by")
    private String closedBy;

    /**
     * Raw id_token issued by the upstream IdP at federated-login time. Stored
     * verbatim so we can replay it as {@code id_token_hint} when cascading
     * RP-initiated logout to the IdP's {@code end_session_endpoint}. WSO2 IS
     * (and stricter implementations) refuse to honor
     * {@code post_logout_redirect_uri} without this hint, falling back to a
     * built-in "logged out" page instead of redirecting the user back to the
     * caller.
     *
     * <p>Nullable because not every login flow goes through an upstream IdP
     * (e.g. M2M client_credentials sessions never have one) and pre-V9 rows
     * naturally won't have it backfilled.
     */
    @Column(name = "upstream_id_token", columnDefinition = "text")
    private String upstreamIdToken;

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) {
            startedAt = Instant.now();
        }
        if (lastSeenAt == null) {
            lastSeenAt = Instant.now();
        }
        if (status == null) {
            status = SessionStatus.ACTIVE;
        }
    }

    /**
     * Checks if the session is currently active based on status and expiration time
     */
    public boolean isActive() {
        return SessionStatus.ACTIVE.equals(status) && expiresAt.isAfter(Instant.now());
    }

    /**
     * Checks if the session is expired based on expiration time
     */
    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }

    /**
     * Closes the session with the specified reason and closed by information
     */
    public void close(String reason, String closedBy) {
        this.status = SessionStatus.CLOSED;
        this.endedAt = Instant.now();
        this.closedReason = reason;
        this.closedBy = closedBy;
        this.lastSeenAt = Instant.now();
    }

    /**
     * Expires the session
     */
    public void expire() {
        this.status = SessionStatus.EXPIRED;
        this.endedAt = Instant.now();
        this.closedReason = "SESSION_TIMEOUT";
        this.closedBy = "SYSTEM";
        this.lastSeenAt = Instant.now();
    }

    /**
     * Revokes the session with the specified reason
     */
    public void revoke(String reason, String revokedBy) {
        this.status = SessionStatus.REVOKED;
        this.endedAt = Instant.now();
        this.closedReason = reason;
        this.closedBy = revokedBy;
        this.lastSeenAt = Instant.now();
    }

    /**
     * Refreshes the session by extending the expiration time and updating last seen
     */
    public void refresh(Instant newExpiresAt) {
        this.expiresAt = newExpiresAt;
        this.lastSeenAt = Instant.now();
    }
}
