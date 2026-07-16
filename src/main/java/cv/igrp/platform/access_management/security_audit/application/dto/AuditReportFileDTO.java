package cv.igrp.platform.access_management.security_audit.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import cv.igrp.platform.access_management.security_audit.domain.enums.ReportFormat;
import cv.igrp.platform.access_management.security_audit.domain.enums.ReportType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * An archived report export.
 *
 * <p>{@link #filePath} is the storage key and the retrieval handle — the client
 * passes it to {@code GET /api/files/url?filePath=...} to obtain a presigned
 * link to the document. No link is embedded here because presigned URLs expire
 * (default 300s), so they are minted on demand rather than persisted.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@IgrpDTO
public class AuditReportFileDTO {

    private String id;
    private ReportType reportType;
    private ReportFormat format;
    /** Pass to {@code GET /api/files/url} to get a presigned download link. */
    private String filePath;
    private String fileName;
    private String contentType;
    private Long sizeBytes;
    private Long rowCount;
    private String filters;
    private Instant periodStart;
    private Instant periodEnd;
    private String generatedBy;
    private Instant generatedAt;
}
