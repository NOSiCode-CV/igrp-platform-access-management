package cv.igrp.platform.access_management.security_audit.application.queries;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.security_audit.application.dto.AuditReportFileDTO;
import cv.igrp.platform.access_management.security_audit.application.export.AuditReportArchiveService;
import cv.igrp.platform.access_management.security_audit.domain.entities.AuditReportFileEntity;
import cv.igrp.platform.access_management.security_audit.domain.enums.ReportFormat;
import cv.igrp.platform.access_management.security_audit.domain.enums.ReportType;
import cv.igrp.platform.access_management.security_audit.infrastructure.persistence.AuditReportFileRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Handles {@link GetAuditReportFilesQuery} — lists archived report exports so a
 * client can pick one and resolve its {@code filePath} to a link via
 * {@code GET /api/files/url}.
 */
@Component
public class GetAuditReportFilesQueryHandler
        implements QueryHandler<GetAuditReportFilesQuery, ResponseEntity<Page<AuditReportFileDTO>>> {

    private final AuditReportFileRepository repository;

    public GetAuditReportFilesQueryHandler(AuditReportFileRepository repository) {
        this.repository = repository;
    }

    @Override
    @IgrpQueryHandler
    public ResponseEntity<Page<AuditReportFileDTO>> handle(GetAuditReportFilesQuery query) {
        Page<AuditReportFileDTO> page = repository.findAll(buildSpec(query), query.getPageable())
                .map(AuditReportArchiveService::toDTO);
        return ResponseEntity.ok(page);
    }

    private static Specification<AuditReportFileEntity> buildSpec(GetAuditReportFilesQuery query) {
        List<Specification<AuditReportFileEntity>> specs = new ArrayList<>();

        enumEquals("reportType", ReportType.class, query.getReportType()).ifPresent(specs::add);
        enumEquals("format", ReportFormat.class, query.getFormat()).ifPresent(specs::add);

        if (query.getGeneratedBy() != null && !query.getGeneratedBy().isBlank()) {
            String pattern = "%" + query.getGeneratedBy().toLowerCase() + "%";
            specs.add((root, q, cb) -> cb.like(cb.lower(root.get("generatedBy")), pattern));
        }

        Specification<AuditReportFileEntity> combined = null;
        for (Specification<AuditReportFileEntity> spec : specs) {
            combined = combined == null ? spec : combined.and(spec);
        }
        return combined;
    }

    /**
     * A filter value that does not parse to the enum yields an always-false
     * predicate (empty result), matching the report handlers' behaviour.
     */
    private static <E extends Enum<E>> Optional<Specification<AuditReportFileEntity>> enumEquals(
            String field, Class<E> type, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Optional.empty();
        }
        Optional<E> parsed = SecurityAuditSpecificationBuilder.parseEnum(type, rawValue);
        return Optional.of(parsed
                .<Specification<AuditReportFileEntity>>map(value -> (root, q, cb) -> cb.equal(root.get(field), value))
                .orElseGet(() -> (root, q, cb) -> cb.disjunction()));
    }
}
