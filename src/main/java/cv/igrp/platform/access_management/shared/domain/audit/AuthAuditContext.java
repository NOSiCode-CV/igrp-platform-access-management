package cv.igrp.platform.access_management.shared.domain.audit;

import jakarta.servlet.http.HttpServletRequest;

public record AuthAuditContext(
    IdentifierType identifierType,
    String identifierValue,
    String userId,
    String applicationCode,
    String sessionId,
    HttpServletRequest request
) {
}
