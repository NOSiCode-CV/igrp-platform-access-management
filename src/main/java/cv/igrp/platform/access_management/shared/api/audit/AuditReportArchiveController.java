package cv.igrp.platform.access_management.shared.api.audit;

import cv.igrp.framework.core.domain.QueryBus;
import cv.igrp.framework.stereotype.IgrpController;
import cv.igrp.platform.access_management.security_audit.application.dto.AuditReportFileDTO;
import cv.igrp.platform.access_management.security_audit.application.export.AuditReportArchiveService;
import cv.igrp.platform.access_management.security_audit.application.export.ReportColumns;
import cv.igrp.platform.access_management.security_audit.application.export.ReportDescriptor;
import cv.igrp.platform.access_management.security_audit.application.export.ReportExportService;
import cv.igrp.platform.access_management.security_audit.application.export.ReportRenderer;
import cv.igrp.platform.access_management.security_audit.application.queries.GetAuditReportFilesQuery;
import cv.igrp.platform.access_management.security_audit.domain.enums.ReportFormat;
import cv.igrp.platform.access_management.security_audit.domain.enums.ReportType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Archives generated report exports to object storage and lists what has been
 * archived (requirements.md §1.7, extended).
 *
 * <p>Unlike the download endpoints on {@code AuditReportsController} — which
 * stream the document straight back — these generate the document, upload it to
 * storage under {@code private/audit-reports/}, and return a record carrying the
 * {@code filePath}. Clients resolve that path to a presigned link via
 * {@code GET /api/files/url?filePath=...}; links are minted on demand because
 * presigned URLs expire.
 *
 * <p>Archiving buffers the whole document in memory: the storage port accepts
 * {@code byte[]} only, so the flat-memory property of the streaming download
 * path (N2) does not apply here. Keep archive requests to report-sized windows.
 *
 * <p>All endpoints are gated by {@code igrp.audit.view} (R4.1).
 */
@RestController("auditReportArchiveController")
@IgrpController
@RequestMapping("/api/auth/reports")
public class AuditReportArchiveController {

    private final QueryBus queryBus;
    private final ReportExportService exportService;
    private final ReportRenderer renderer;
    private final AuditReportArchiveService archiveService;
    private final ObjectMapper objectMapper;

    public AuditReportArchiveController(QueryBus queryBus,
                                        ReportExportService exportService,
                                        ReportRenderer renderer,
                                        AuditReportArchiveService archiveService,
                                        ObjectMapper objectMapper) {
        this.queryBus = queryBus;
        this.exportService = exportService;
        this.renderer = renderer;
        this.archiveService = archiveService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/audit/archive")
    @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_AUDIT_VIEW)")
    public ResponseEntity<AuditReportFileDTO> archiveAuditReport(
            @RequestParam ReportFormat format,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String accessRole,
            @RequestParam(required = false) String operationState,
            @RequestParam(required = false) String authorizedBy,
            @RequestParam(required = false) String status) {

        return archive(ReportColumns.AUDIT, ReportType.AUDIT, format, startDate, endDate,
                filters("username", username, "module", module, "accessRole", accessRole,
                        "operationState", operationState, "authorizedBy", authorizedBy, "status", status),
                () -> exportService.streamAuditRows(
                        toLocal(startDate), toLocal(endDate),
                        username, module, accessRole, operationState, authorizedBy, status));
    }

    @PostMapping("/access/archive")
    @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_AUDIT_VIEW)")
    public ResponseEntity<AuditReportFileDTO> archiveAccessReport(
            @RequestParam ReportFormat format,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String status) {

        return archive(ReportColumns.ACCESS, ReportType.ACCESS, format, startDate, endDate,
                filters("username", username, "role", role, "module", module,
                        "action", action, "status", status),
                () -> exportService.streamAccessRows(
                        toLocal(startDate), toLocal(endDate), username, role, module, action, status));
    }

    @PostMapping("/settings/archive")
    @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_AUDIT_VIEW)")
    public ResponseEntity<AuditReportFileDTO> archiveSettingsReport(
            @RequestParam ReportFormat format,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(required = false) String performedBy,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String entityName) {

        return archive(ReportColumns.SETTINGS, ReportType.SETTINGS, format, startDate, endDate,
                filters("performedBy", performedBy, "area", area, "entityType", entityType,
                        "operation", operation, "entityName", entityName),
                () -> exportService.streamSettingsRows(
                        toLocal(startDate), toLocal(endDate), performedBy, area, entityType, operation, entityName));
    }

    /** Lists archived exports, newest first. */
    @GetMapping("/archives")
    @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_AUDIT_VIEW)")
    public ResponseEntity<Page<AuditReportFileDTO>> listArchives(
            @RequestParam(required = false) String reportType,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String generatedBy,
            Pageable pageable) {
        return queryBus.handle(new GetAuditReportFilesQuery(reportType, format, generatedBy, pageable));
    }

    // -------------------------------------------------------------- helpers

    private <T> ResponseEntity<AuditReportFileDTO> archive(
            ReportDescriptor<T> descriptor, ReportType reportType, ReportFormat format,
            Instant startDate, Instant endDate, Map<String, Object> filters, Supplier<Stream<T>> rows) {

        AtomicLong rowCount = new AtomicLong();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        renderer.render(buffer, descriptor, format, rows.get().peek(row -> rowCount.incrementAndGet()));

        AuditReportFileDTO archived = archiveService.archive(
                reportType,
                format,
                renderer.fileName(descriptor, format),
                buffer.toByteArray(),
                rowCount.get(),
                toJson(filters),
                startDate,
                endDate);
        return ResponseEntity.ok(archived);
    }

    /** Snapshot of the filters used, stored on the archive record for traceability. */
    private String toJson(Map<String, Object> filters) {
        try {
            return objectMapper.writeValueAsString(filters);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    /** Builds an ordered map of the supplied key/value pairs, dropping null values. */
    private static Map<String, Object> filters(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            Object value = keyValues[i + 1];
            if (value != null) {
                map.put((String) keyValues[i], value);
            }
        }
        return map;
    }

    /** The audit {@code timestamp} column is a {@code LocalDateTime} (system zone). */
    private static LocalDateTime toLocal(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
