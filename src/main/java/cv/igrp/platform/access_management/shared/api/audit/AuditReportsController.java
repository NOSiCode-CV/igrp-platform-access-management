package cv.igrp.platform.access_management.shared.api.audit;

import cv.igrp.framework.core.domain.QueryBus;
import cv.igrp.framework.stereotype.IgrpController;
import cv.igrp.platform.access_management.security_audit.application.dto.AccessReportRowDTO;
import cv.igrp.platform.access_management.security_audit.application.dto.AuditReportRowDTO;
import cv.igrp.platform.access_management.security_audit.application.dto.SettingsReportRowDTO;
import cv.igrp.platform.access_management.security_audit.application.export.ExcelReportExporter;
import cv.igrp.platform.access_management.security_audit.application.export.PdfReportExporter;
import cv.igrp.platform.access_management.security_audit.application.export.ReportColumns;
import cv.igrp.platform.access_management.security_audit.application.export.ReportDescriptor;
import cv.igrp.platform.access_management.security_audit.application.export.ReportExportService;
import cv.igrp.platform.access_management.security_audit.application.queries.GetAccessReportQuery;
import cv.igrp.platform.access_management.security_audit.application.queries.GetAuditReportQuery;
import cv.igrp.platform.access_management.security_audit.application.queries.GetSettingsReportQuery;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Reporting surface over the unified audit log. Three paginated, filtered JSON
 * views (Audit / Access / Settings) that project {@code t_security_audit_log}
 * rows through the report DTOs (requirements.md §1.5), plus (Phase 4) PDF and
 * Excel export variants of each (§1.7).
 *
 * <p>All endpoints are gated by the single {@code igrp.audit.view} permission
 * (R4.1). {@code startDate}/{@code endDate} are required — a missing bound yields
 * a 400. Export endpoints accept the same filters as their JSON siblings, minus
 * pagination, and stream every matching row (R7.2/R7.3).
 */
@RestController("auditReportsController")
@IgrpController
@RequestMapping("/api/auth/reports")
public class AuditReportsController {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final QueryBus queryBus;
    private final ReportExportService exportService;
    private final ExcelReportExporter excelExporter;
    private final PdfReportExporter pdfExporter;
    private final MessageSource messageSource;

    public AuditReportsController(QueryBus queryBus,
                                 ReportExportService exportService,
                                 ExcelReportExporter excelExporter,
                                 PdfReportExporter pdfExporter,
                                 MessageSource messageSource) {
        this.queryBus = queryBus;
        this.exportService = exportService;
        this.excelExporter = excelExporter;
        this.pdfExporter = pdfExporter;
        this.messageSource = messageSource;
    }

    // ----------------------------------------------------------------- JSON

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

    // -------------------------------------------------------------- Exports

    @GetMapping("/audit.xlsx")
    @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_AUDIT_VIEW)")
    public ResponseEntity<StreamingResponseBody> auditReportXlsx(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String accessRole,
            @RequestParam(required = false) String operationState,
            @RequestParam(required = false) String authorizedBy,
            @RequestParam(required = false) String status) {
        return excel(ReportColumns.AUDIT, () -> exportService.streamAuditRows(
                toLocal(startDate), toLocal(endDate),
                username, module, accessRole, operationState, authorizedBy, status));
    }

    @GetMapping("/audit.pdf")
    @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_AUDIT_VIEW)")
    public ResponseEntity<StreamingResponseBody> auditReportPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String accessRole,
            @RequestParam(required = false) String operationState,
            @RequestParam(required = false) String authorizedBy,
            @RequestParam(required = false) String status) {
        return pdf(ReportColumns.AUDIT, () -> exportService.streamAuditRows(
                toLocal(startDate), toLocal(endDate),
                username, module, accessRole, operationState, authorizedBy, status));
    }

    @GetMapping("/access.xlsx")
    @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_AUDIT_VIEW)")
    public ResponseEntity<StreamingResponseBody> accessReportXlsx(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String status) {
        return excel(ReportColumns.ACCESS, () -> exportService.streamAccessRows(
                toLocal(startDate), toLocal(endDate), username, role, module, action, status));
    }

    @GetMapping("/access.pdf")
    @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_AUDIT_VIEW)")
    public ResponseEntity<StreamingResponseBody> accessReportPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String status) {
        return pdf(ReportColumns.ACCESS, () -> exportService.streamAccessRows(
                toLocal(startDate), toLocal(endDate), username, role, module, action, status));
    }

    @GetMapping("/settings.xlsx")
    @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_AUDIT_VIEW)")
    public ResponseEntity<StreamingResponseBody> settingsReportXlsx(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(required = false) String performedBy,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String entityName) {
        return excel(ReportColumns.SETTINGS, () -> exportService.streamSettingsRows(
                toLocal(startDate), toLocal(endDate), performedBy, area, entityType, operation, entityName));
    }

    @GetMapping("/settings.pdf")
    @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_AUDIT_VIEW)")
    public ResponseEntity<StreamingResponseBody> settingsReportPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(required = false) String performedBy,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String entityName) {
        return pdf(ReportColumns.SETTINGS, () -> exportService.streamSettingsRows(
                toLocal(startDate), toLocal(endDate), performedBy, area, entityType, operation, entityName));
    }

    // -------------------------------------------------------------- helpers

    private <T> ResponseEntity<StreamingResponseBody> excel(
            ReportDescriptor<T> descriptor, Supplier<Stream<T>> rows) {
        StreamingResponseBody body = out -> excelExporter.write(out, descriptor.columns(), rows.get());
        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition(descriptor.filenamePrefix(), "xlsx"))
                .body(body);
    }

    private <T> ResponseEntity<StreamingResponseBody> pdf(
            ReportDescriptor<T> descriptor, Supplier<Stream<T>> rows) {
        String headerText = title(descriptor) + " generated on " + LocalDate.now();
        StreamingResponseBody body = out -> pdfExporter.write(out, headerText, descriptor.columns(), rows.get());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition(descriptor.filenamePrefix(), "pdf"))
                .body(body);
    }

    private String title(ReportDescriptor<?> descriptor) {
        return messageSource.getMessage(
                descriptor.titleMessageKey(), null, descriptor.defaultTitle(),
                LocaleContextHolder.getLocale());
    }

    private static String disposition(String prefix, String ext) {
        String filename = prefix + "-" + LocalDate.now() + "." + ext;
        return ContentDisposition.attachment().filename(filename).build().toString();
    }

    /** The audit {@code timestamp} column is a {@code LocalDateTime} (system zone). */
    private static LocalDateTime toLocal(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
