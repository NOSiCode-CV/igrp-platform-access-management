package cv.igrp.platform.access_management.security_audit.application.queries;

import cv.igrp.framework.core.domain.Query;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

/**
 * Filtered, paginated query backing the Audit Report
 * ({@code GET /api/auth/reports/audit}). {@code startDate}/{@code endDate} are
 * required (enforced at the controller); the rest are optional.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetAuditReportQuery implements Query {

    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String username;
    private String module;
    private String accessRole;
    private String operationState;
    private String authorizedBy;
    private String status;
    private Pageable pageable;
}
