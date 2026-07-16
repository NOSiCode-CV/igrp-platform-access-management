package cv.igrp.platform.access_management.security_audit.application.export;

import cv.igrp.platform.access_management.files.application.constants.UploadType;
import cv.igrp.platform.access_management.security_audit.application.dto.AuditReportFileDTO;
import cv.igrp.platform.access_management.security_audit.domain.entities.AuditReportFileEntity;
import cv.igrp.platform.access_management.security_audit.domain.enums.ReportFormat;
import cv.igrp.platform.access_management.security_audit.domain.enums.ReportType;
import cv.igrp.platform.access_management.security_audit.infrastructure.persistence.AuditReportFileRepository;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpErrorCode;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import cv.igrp.platform.filemanager.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Uploads a generated report to object storage and records it in
 * {@code t_audit_report_file}.
 *
 * <p>Calls {@link StorageService} directly rather than going through
 * {@code UploadFileCommandHandler}: that handler takes a {@code MultipartFile}
 * (an inbound upload), whereas these bytes are generated server-side. The key
 * convention is kept identical to it — {@code private/<folder>/<user>/<uuid>_<name>}
 * — so archived reports resolve through {@code GET /api/files/url}, which only
 * accepts paths starting with {@code private/} or {@code public/}.
 *
 * <p>Reports are stored privately: they contain usernames, IP addresses and
 * administrative history, so access stays gated behind a presigned URL rather
 * than a public object.
 */
@Service
public class AuditReportArchiveService {

    private static final Logger log = LoggerFactory.getLogger(AuditReportArchiveService.class);

    /** Storage folder for every archived report export. */
    static final String FOLDER = "audit-reports";

    private final StorageService storageService;
    private final AuditReportFileRepository repository;
    private final AuthenticationHelper authenticationHelper;

    public AuditReportArchiveService(StorageService storageService,
                                     AuditReportFileRepository repository,
                                     AuthenticationHelper authenticationHelper) {
        this.storageService = storageService;
        this.repository = repository;
        this.authenticationHelper = authenticationHelper;
    }

    /**
     * Uploads {@code content} and persists the archive record.
     *
     * @param fileName the document name, e.g. {@code audit-report-2026-07-16.xlsx}
     * @return the persisted record, carrying the {@code filePath} handle
     */
    public AuditReportFileDTO archive(ReportType reportType,
                                      ReportFormat format,
                                      String fileName,
                                      byte[] content,
                                      long rowCount,
                                      String filters,
                                      Instant periodStart,
                                      Instant periodEnd) {

        String generatedBy = authenticationHelper.getSub();
        String filePath = String.format("%s/%s/%s/%s_%s",
                UploadType.PRIVATE.name().toLowerCase(),
                FOLDER,
                generatedBy,
                UUID.randomUUID(),
                fileName);

        try {
            storageService.uploadFile(content, filePath, format.contentType());
        } catch (Exception e) {
            log.error("Failed to upload archived {} {} report to storage at '{}'",
                    format, reportType, filePath, e);
            throw IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_FILE_UPLOAD_FAILED);
        }

        AuditReportFileEntity entity = new AuditReportFileEntity();
        entity.setReportType(reportType);
        entity.setFormat(format);
        entity.setFilePath(filePath);
        entity.setFileName(fileName);
        entity.setContentType(format.contentType());
        entity.setSizeBytes((long) content.length);
        entity.setRowCount(rowCount);
        entity.setFilters(filters);
        entity.setPeriodStart(periodStart);
        entity.setPeriodEnd(periodEnd);
        entity.setGeneratedBy(generatedBy);
        entity.setGeneratedAt(Instant.now());

        AuditReportFileEntity saved = repository.save(entity);
        log.info("Archived {} {} report ({} rows, {} bytes) at '{}'",
                format, reportType, rowCount, content.length, filePath);
        return toDTO(saved);
    }

    /** Projects an archive record onto its DTO. Shared with the list query handler. */
    public static AuditReportFileDTO toDTO(AuditReportFileEntity e) {
        AuditReportFileDTO dto = new AuditReportFileDTO();
        dto.setId(e.getId() != null ? e.getId().toString() : null);
        dto.setReportType(e.getReportType());
        dto.setFormat(e.getFormat());
        dto.setFilePath(e.getFilePath());
        dto.setFileName(e.getFileName());
        dto.setContentType(e.getContentType());
        dto.setSizeBytes(e.getSizeBytes());
        dto.setRowCount(e.getRowCount());
        dto.setFilters(e.getFilters());
        dto.setPeriodStart(e.getPeriodStart());
        dto.setPeriodEnd(e.getPeriodEnd());
        dto.setGeneratedBy(e.getGeneratedBy());
        dto.setGeneratedAt(e.getGeneratedAt());
        return dto;
    }
}
