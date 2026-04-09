package cv.igrp.platform.access_management.shared.api.audit;

import cv.igrp.platform.access_management.shared.domain.audit.AuthEventType;
import cv.igrp.platform.access_management.shared.domain.audit.IdentifierType;
import java.time.Instant;
import java.util.UUID;

public record AuthAuditLogDTO(
    UUID id,
    AuthEventType eventType,
    IdentifierType identifierType,
    String identifierValue,
    String userId,
    String applicationCode,
    String ipAddress,
    String userAgent,
    String sessionId,
    String failureReason,
    Instant timestamp,
    String environment
) {}
