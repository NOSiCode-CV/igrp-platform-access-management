package cv.igrp.platform.access_management.security_audit.application.export;

import cv.igrp.platform.access_management.security_audit.application.dto.AuditReportFileDTO;
import cv.igrp.platform.access_management.security_audit.domain.entities.AuditReportFileEntity;
import cv.igrp.platform.access_management.security_audit.domain.enums.ReportFormat;
import cv.igrp.platform.access_management.security_audit.domain.enums.ReportType;
import cv.igrp.platform.access_management.security_audit.infrastructure.persistence.AuditReportFileRepository;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import cv.igrp.platform.filemanager.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditReportArchiveServiceTest {

    @Mock private StorageService storageService;
    @Mock private AuditReportFileRepository repository;
    @Mock private AuthenticationHelper authenticationHelper;

    private AuditReportArchiveService service;

    private final byte[] content = "some-report-bytes".getBytes();

    @BeforeEach
    void setUp() {
        service = new AuditReportArchiveService(storageService, repository, authenticationHelper);
    }

    @Test
    void uploadsUnderPrivateAuditReportsKeyAndPersistsTheRecord() throws Exception {
        when(authenticationHelper.getSub()).thenReturn("marcelo.monteiro@nosi.cv");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuditReportFileDTO dto = service.archive(
                ReportType.AUDIT, ReportFormat.XLSX, "audit-report-2026-07-16.xlsx",
                content, 42L, "{\"status\":\"SUCCESS\"}",
                Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-30T00:00:00Z"));

        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        verify(storageService).uploadFile(
                any(byte[].class), pathCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(ReportFormat.XLSX.contentType()));

        String path = pathCaptor.getValue();
        // GET /api/files/url only resolves paths starting with private/ or public/.
        assertThat(path).startsWith("private/audit-reports/marcelo.monteiro@nosi.cv/");
        assertThat(path).endsWith("_audit-report-2026-07-16.xlsx");

        ArgumentCaptor<AuditReportFileEntity> entityCaptor =
                ArgumentCaptor.forClass(AuditReportFileEntity.class);
        verify(repository).save(entityCaptor.capture());
        AuditReportFileEntity saved = entityCaptor.getValue();
        assertThat(saved.getReportType()).isEqualTo(ReportType.AUDIT);
        assertThat(saved.getFormat()).isEqualTo(ReportFormat.XLSX);
        assertThat(saved.getFilePath()).isEqualTo(path);
        assertThat(saved.getFileName()).isEqualTo("audit-report-2026-07-16.xlsx");
        assertThat(saved.getSizeBytes()).isEqualTo((long) content.length);
        assertThat(saved.getRowCount()).isEqualTo(42L);
        assertThat(saved.getGeneratedBy()).isEqualTo("marcelo.monteiro@nosi.cv");
        assertThat(saved.getGeneratedAt()).isNotNull();

        // The returned filePath is the handle the client feeds to GET /api/files/url.
        assertThat(dto.getFilePath()).isEqualTo(path);
        assertThat(dto.getRowCount()).isEqualTo(42L);
    }

    @Test
    void keysCsvAndPdfWithTheirOwnContentTypes() throws Exception {
        when(authenticationHelper.getSub()).thenReturn("someone");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.archive(ReportType.SETTINGS, ReportFormat.CSV, "settings-report.csv",
                content, 1L, "{}", null, null);
        // Stored with the charset — CSV has no in-band encoding declaration, so a
        // presigned GET needs the header to decode the UTF-8 payload correctly.
        verify(storageService).uploadFile(any(), anyString(),
                org.mockito.ArgumentMatchers.eq("text/csv;charset=UTF-8"));

        service.archive(ReportType.ACCESS, ReportFormat.PDF, "access-report.pdf",
                content, 1L, "{}", null, null);
        verify(storageService).uploadFile(any(), anyString(),
                org.mockito.ArgumentMatchers.eq("application/pdf"));
    }

    @Test
    void doesNotPersistARecordWhenTheUploadFails() throws Exception {
        when(authenticationHelper.getSub()).thenReturn("someone");
        doThrow(new RuntimeException("minio down"))
                .when(storageService).uploadFile(any(), anyString(), anyString());

        assertThatThrownBy(() -> service.archive(
                ReportType.AUDIT, ReportFormat.XLSX, "audit-report.xlsx",
                content, 1L, "{}", null, null))
                .isInstanceOf(IgrpResponseStatusException.class)
                // 503, not 400: the request was fine, our storage was not. Also
                // keeps a storage outage visible to 5xx alerting.
                .satisfies(e -> assertThat(((IgrpResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        // No dangling row pointing at a file that was never stored.
        verify(repository, never()).save(any());
    }
}
