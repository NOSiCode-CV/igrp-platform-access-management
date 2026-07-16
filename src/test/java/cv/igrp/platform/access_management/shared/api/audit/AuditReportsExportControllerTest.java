package cv.igrp.platform.access_management.shared.api.audit;

import cv.igrp.framework.core.domain.QueryBus;
import cv.igrp.platform.access_management.security_audit.application.dto.AuditReportRowDTO;
import cv.igrp.platform.access_management.security_audit.application.export.ReportColumns;
import cv.igrp.platform.access_management.security_audit.application.export.ReportExportService;
import cv.igrp.platform.access_management.security_audit.application.export.ReportRenderer;
import cv.igrp.platform.access_management.security_audit.domain.enums.ReportFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Contract tests for the nine Phase 4 export endpoints on
 * {@link AuditReportsController}: correct {@code Content-Type},
 * {@code Content-Disposition} filename, and delegation to the renderer with the
 * right format (validation.md §Phase 4 contract).
 */
@ExtendWith(MockitoExtension.class)
class AuditReportsExportControllerTest {

    @Mock private QueryBus queryBus;
    @Mock private ReportExportService exportService;
    @Mock private ReportRenderer renderer;

    private AuditReportsController controller;

    private final Instant start = Instant.parse("2026-06-01T00:00:00Z");
    private final Instant end = Instant.parse("2026-06-30T00:00:00Z");

    @BeforeEach
    void setUp() {
        controller = new AuditReportsController(queryBus, exportService, renderer);
    }

    private void stubAuditRows() {
        when(exportService.streamAuditRows(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Stream.<AuditReportRowDTO>empty());
    }

    @Test
    void auditXlsxSetsSpreadsheetContentTypeAndFilenameAndRendersAsXlsx() throws Exception {
        stubAuditRows();
        when(renderer.fileName(any(), eq(ReportFormat.XLSX))).thenReturn("audit-report-2026-07-16.xlsx");

        ResponseEntity<StreamingResponseBody> response = controller.auditReportXlsx(
                start, end, null, null, null, null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"audit-report-2026-07-16.xlsx\"");

        response.getBody().writeTo(new ByteArrayOutputStream());
        verify(renderer).render(any(), eq(ReportColumns.AUDIT), eq(ReportFormat.XLSX), any());
    }

    @Test
    void auditPdfSetsPdfContentTypeAndRendersAsPdf() throws Exception {
        stubAuditRows();
        when(renderer.fileName(any(), eq(ReportFormat.PDF))).thenReturn("audit-report-2026-07-16.pdf");

        ResponseEntity<StreamingResponseBody> response = controller.auditReportPdf(
                start, end, null, null, null, null, null, null);

        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).endsWith(".pdf\"");

        response.getBody().writeTo(new ByteArrayOutputStream());
        verify(renderer).render(any(), eq(ReportColumns.AUDIT), eq(ReportFormat.PDF), any());
    }

    @Test
    void auditCsvSetsCsvContentTypeAndRendersAsCsv() throws Exception {
        stubAuditRows();
        when(renderer.fileName(any(), eq(ReportFormat.CSV))).thenReturn("audit-report-2026-07-16.csv");

        ResponseEntity<StreamingResponseBody> response = controller.auditReportCsv(
                start, end, null, null, null, null, null, null);

        // Charset must be on the wire: the payload is UTF-8 and CSV declares no
        // encoding in-band, so a bare text/csv leaves clients guessing.
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.parseMediaType("text/csv;charset=UTF-8"));
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).endsWith(".csv\"");

        response.getBody().writeTo(new ByteArrayOutputStream());
        verify(renderer).render(any(), eq(ReportColumns.AUDIT), eq(ReportFormat.CSV), any());
    }

    @Test
    void settingsCsvUsesSettingsDescriptor() {
        when(renderer.fileName(any(), eq(ReportFormat.CSV))).thenReturn("settings-report-2026-07-16.csv");

        ResponseEntity<StreamingResponseBody> response = controller.settingsReportCsv(
                start, end, null, null, null, null, null);

        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"settings-report-2026-07-16.csv\"");
    }

    @Test
    void accessXlsxUsesAccessDescriptor() {
        when(renderer.fileName(any(), eq(ReportFormat.XLSX))).thenReturn("access-report-2026-07-16.xlsx");

        ResponseEntity<StreamingResponseBody> response = controller.accessReportXlsx(
                start, end, null, null, null, null, null);

        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("access-report-").endsWith(".xlsx\"");
    }
}
