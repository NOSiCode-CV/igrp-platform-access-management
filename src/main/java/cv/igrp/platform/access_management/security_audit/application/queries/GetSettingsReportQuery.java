package cv.igrp.platform.access_management.security_audit.application.queries;

import cv.igrp.framework.core.domain.Query;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

/**
 * Filtered, paginated query backing the Settings Report
 * ({@code GET /api/auth/reports/settings}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetSettingsReportQuery implements Query {

    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String performedBy;
    private String area;
    private String entityType;
    private String operation;
    private String entityName;
    private Pageable pageable;
}
