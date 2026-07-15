package cv.igrp.platform.access_management.security_audit.application.queries;

import cv.igrp.framework.core.domain.Query;
import org.springframework.data.domain.Pageable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * Filtered, paginated query over the unified security audit log
 * (backs {@code GET /api/auth/audit}). All filters are optional; null means
 * "no restriction". The GENESIS anchor row is always excluded by the handler.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetSecurityAuditLogsQuery implements Query {

    private String userId;
    private String username;
    private String eventType;
    private String category;
    private String ipAddress;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Pageable pageable;

}
