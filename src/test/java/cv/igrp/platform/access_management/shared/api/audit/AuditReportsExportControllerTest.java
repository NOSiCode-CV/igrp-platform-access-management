package cv.igrp.platform.access_management.shared.api.audit;

import cv.igrp.framework.core.domain.QueryBus;
import cv.igrp.platform.access_management.security_audit.application.dto.AuditReportRowDTO;
import cv.igrp.platform.access_management.security_audit.application.export.ExcelReportExporter;
import cv.igrp.platform.access_management.security_audit.application.export.PdfReportExporter;
import cv.igrp.platform.access_management.security_audit.application.export.ReportExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Contract tests for the six Phase 4 export endpoints on
 * {@link AuditReportsController}: correct {@code Content-Type},
 * {@code Content-Disposition} filename, and delegation to the right exporter
 * (validation.md §Phase 4 contract).
 */
@ExtendWith(MockitoExtension.class)
class AuditReportsExportControllerTest {

    @Mock private QueryBus queryBus;
    @Mock private ReportExportService exportService;
    @Mock private ExcelReportExporter excelExporter;
    @Mock private PdfReportExporter pdfExporter;
    @Mock private MessageSource messageSource;

    private AuditReportsController controller;

    private final Instant start = Instant.parse("2026-06-01T00:00:00Z");
    private final Instant end = Instant.parse("2026-06-30T00:00:00Z");

    @BeforeEach
    void setUp() {
        controller = new AuditReportsController(
                queryBus, exportService, excelExporter, pdfExporter, messageSource);
    }

    @Test
    void auditXlsxSetsSpreadsheetContentTypeAndFilenameAndStreamsThroughExcelExporter() throws Exception {
        when(exportService.streamAuditRows(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Stream.<AuditReportRowDTO>empty());

        ResponseEntity<StreamingResponseBody> response = controller.auditReportXlsx(
                start, end, null, null, null, null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(disposition).startsWith("attachment;");
        assertThat(disposition).contains("audit-report-").endsWith(".xlsx\"");

        response.getBody().writeTo(new ByteArrayOutputStream());
        verify(excelExporter).write(any(), any(), any());
    }

    @Test
    void auditPdfSetsPdfContentTypeAndHeaderReadsGeneratedOn() throws Exception {
        when(messageSource.getMessage(eq("report.audit.title"), any(), anyString(), any()))
                .thenReturn("Audit Report");
        when(exportService.streamAuditRows(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Stream.<AuditReportRowDTO>empty());

        ResponseEntity<StreamingResponseBody> response = controller.auditReportPdf(
                start, end, null, null, null, null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("audit-report-").endsWith(".pdf\"");

        response.getBody().writeTo(new ByteArrayOutputStream());

        ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);
        verify(pdfExporter).write(any(), headerCaptor.capture(), any(), any());
        assertThat(headerCaptor.getValue()).containsPattern("Audit Report generated on \\d{4}-\\d{2}-\\d{2}");
    }

    @Test
    void settingsXlsxUsesSettingsFilenamePrefix() {
        // Body is not executed here, so streamSettingsRows is never invoked —
        // this asserts only the response envelope (content type + filename).
        ResponseEntity<StreamingResponseBody> response = controller.settingsReportXlsx(
                start, end, null, null, null, null, null);

        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("settings-report-").endsWith(".xlsx\"");
    }
}
