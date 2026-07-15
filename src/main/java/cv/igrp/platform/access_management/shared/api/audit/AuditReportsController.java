package cv.igrp.platform.access_management.shared.api.audit;

import cv.igrp.framework.core.domain.QueryBus;
import cv.igrp.framework.stereotype.IgrpController;
import cv.igrp.platform.access_management.security_audit.application.dto.AccessReportRowDTO;
import cv.igrp.platform.access_management.security_audit.application.dto.AuditReportRowDTO;
import cv.igrp.platform.access_management.security_audit.application.dto.SettingsReportRowDTO;
import cv.igrp.platform.access_management.security_audit.application.queries.GetAccessReportQuery;
import cv.igrp.platform.access_management.security_audit.application.queries.GetAuditReportQuery;
import cv.igrp.platform.access_management.security_audit.application.queries.GetSettingsReportQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Reporting surface over the unified audit log. Three paginated, filtered JSON
 * views (Audit / Access / Settings) that project {@code t_security_audit_log}
 * rows through the report DTOs (requirements.md §1.5).
 *
 * <p>All three are gated by the single {@code igrp.audit.view} permission (R4.1),
 * shared with the raw audit list and (Phase 4) the export endpoints.
 * {@code startDate}/{@code endDate} are required — a missing bound yields a 400.
 */
@RestController("auditReportsController")
@IgrpController
@RequestMapping("/api/auth/reports")
public class AuditReportsController {

    private final QueryBus queryBus;

    public AuditReportsController(QueryBus queryBus) {
        this.queryBus = queryBus;
    }

    @GetMapping("/audit")
    @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_AUDIT_VIEW)")
    public ResponseEntity<Page<AuditReportRowDTO>> auditReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String accessRole,
            @RequestParam(required = false) String operationState,
            @RequestParam(required = false) String authorizedBy,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        return queryBus.handle(new GetAuditReportQuery(
                toLocal(startDate), toLocal(endDate),
                username, module, accessRole, operationState, authorizedBy, status, pageable));
    }

    @GetMapping("/access")
    @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_AUDIT_VIEW)")
    public ResponseEntity<Page<AccessReportRowDTO>> accessReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        return queryBus.handle(new GetAccessReportQuery(
                toLocal(startDate), toLocal(endDate),
                username, role, module, action, status, pageable));
    }

    @GetMapping("/settings")
    @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_AUDIT_VIEW)")
    public ResponseEntity<Page<SettingsReportRowDTO>> settingsReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(required = false) String performedBy,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String entityName,
            Pageable pageable) {
        return queryBus.handle(new GetSettingsReportQuery(
                toLocal(startDate), toLocal(endDate),
                performedBy, area, entityType, operation, entityName, pageable));
    }

    /** The audit {@code timestamp} column is a {@code LocalDateTime} (system zone). */
    private static LocalDateTime toLocal(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
