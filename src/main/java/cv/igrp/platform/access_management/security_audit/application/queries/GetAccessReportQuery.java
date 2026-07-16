package cv.igrp.platform.access_management.security_audit.application.queries;

import cv.igrp.framework.core.domain.Query;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

/**
 * Filtered, paginated query backing the Access Report
 * ({@code GET /api/auth/reports/access}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetAccessReportQuery implements Query {

    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String username;
    private String role;
    private String module;
    private String action;
    private String status;
    private Pageable pageable;
}
