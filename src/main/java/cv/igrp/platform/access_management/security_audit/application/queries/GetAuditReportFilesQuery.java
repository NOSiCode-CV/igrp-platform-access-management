package cv.igrp.platform.access_management.security_audit.application.queries;

import cv.igrp.framework.core.domain.Query;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Pageable;

/**
 * Filtered, paginated query listing archived report exports
 * ({@code GET /api/auth/reports/archives}). All filters are optional.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetAuditReportFilesQuery implements Query {

    private String reportType;
    private String format;
    private String generatedBy;
    private Pageable pageable;
}
