package cv.igrp.platform.access_management.oauth_server.application.dto;

import cv.igrp.platform.access_management.oauth_server.domain.models.AuthEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthAuditDTO {

    private UUID id;
    private String username;
    private AuthEventType eventType;
    private String ipAddress;
    private String userAgent;
    private String sessionId;
    private LocalDateTime timestamp;
}
