package cv.igrp.platform.access_management.security_audit.application.export;

import cv.igrp.platform.access_management.security_audit.application.dto.AccessReportRowDTO;
import cv.igrp.platform.access_management.security_audit.application.dto.AuditReportRowDTO;
import cv.igrp.platform.access_management.security_audit.application.dto.SettingsReportRowDTO;
import cv.igrp.platform.access_management.security_audit.application.queries.ReportRowMapper;
import cv.igrp.platform.access_management.security_audit.application.queries.ReportSpecifications;
import cv.igrp.platform.access_management.security_audit.domain.entities.SecurityAuditLogEntity;
import cv.igrp.platform.access_management.security_audit.infrastructure.persistence.SecurityAuditLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.stream.Stream;

/**
 * Streams filtered report rows for export. Reuses {@link ReportSpecifications}
 * (identical filter contract to the JSON siblings, R7.2) and
 * {@link ReportRowMapper} (identical projection), pulling rows in chunks via
 * {@link PagedStream} so memory stays flat for large exports (R7.3).
 *
 * <p>Rows stream in chain order ({@code sequence_number} ascending) for a
 * deterministic export sequence.
 */
@Component
public class ReportExportService {

    /** Rows fetched per DB round-trip while streaming an export. */
    static final int CHUNK_SIZE = 1_000;

    private static final Sort EXPORT_SORT = Sort.by(Sort.Direction.ASC, "sequenceNumber");

    private final SecurityAuditLogRepository repository;

    public ReportExportService(SecurityAuditLogRepository repository) {
        this.repository = repository;
    }

    public Stream<AuditReportRowDTO> streamAuditRows(
            LocalDateTime startDate, LocalDateTime endDate,
            String username, String module, String accessRole,
            String operationState, String authorizedBy, String status) {
        Specification<SecurityAuditLogEntity> spec = ReportSpecifications.audit(
                startDate, endDate, username, module, accessRole, operationState, authorizedBy, status);
        return stream(spec, ReportRowMapper::toAuditRow);
    }

    public Stream<AccessReportRowDTO> streamAccessRows(
            LocalDateTime startDate, LocalDateTime endDate,
            String username, String role, String module, String action, String status) {
        Specification<SecurityAuditLogEntity> spec = ReportSpecifications.access(
                startDate, endDate, username, role, module, action, status);
        return stream(spec, ReportRowMapper::toAccessRow);
    }

    public Stream<SettingsReportRowDTO> streamSettingsRows(
            LocalDateTime startDate, LocalDateTime endDate,
            String performedBy, String area, String entityType,
            String operation, String entityName) {
        Specification<SecurityAuditLogEntity> spec = ReportSpecifications.settings(
                startDate, endDate, performedBy, area, entityType, operation, entityName);
        return stream(spec, ReportRowMapper::toSettingsRow);
    }

    private <T> Stream<T> stream(
            Specification<SecurityAuditLogEntity> spec,
            java.util.function.Function<SecurityAuditLogEntity, T> mapper) {
        return PagedStream.of(
                pageable -> repository.findAll(spec, pageable),
                mapper,
                PageRequest.of(0, CHUNK_SIZE, EXPORT_SORT));
    }
}
