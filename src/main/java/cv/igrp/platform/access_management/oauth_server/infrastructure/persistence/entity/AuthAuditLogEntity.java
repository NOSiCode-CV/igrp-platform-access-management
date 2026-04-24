package cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity;

import cv.igrp.platform.access_management.oauth_server.domain.models.AuthEventType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Authentication / authorization event captured for basic audit visibility
 * at the authorization server layer.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "t_auth_audit_log", indexes = {
        @Index(name = "idx_auth_audit_username", columnList = "username"),
        @Index(name = "idx_auth_audit_event_type", columnList = "event_type")
})
public class AuthAuditLogEntity {

    @Id
    @Column(name = "id", unique = true, nullable = false)
    private UUID id;

    @Column(name = "username")
    private String username;

    @Column(name = "event_type", nullable = false, length = 40)
    @Enumerated(EnumType.STRING)
    private AuthEventType eventType;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "session_id", length = 120)
    private String sessionId;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
}
