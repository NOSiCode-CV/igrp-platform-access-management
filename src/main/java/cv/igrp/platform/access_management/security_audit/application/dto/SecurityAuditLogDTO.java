package cv.igrp.platform.access_management.security_audit.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Data Transfer Object for Security Audit Logs.
 * This DTO represents the structure of an audit log entry as exposed by the API.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@IgrpDTO
public class SecurityAuditLogDTO {

    private Long id;
    private String userId;
    private String username;
    private String sessionId;
    private String ipAddress;
    private String userAgent;
    private String correlationId;
    private String requestPath;
    private String decisionReason;
    private String eventType;
    private String category;
    private String contextData;
    private String timestamp;

}
