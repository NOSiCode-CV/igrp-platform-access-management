package cv.igrp.platform.access_management.security_audit.application.queries;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.security_audit.application.dto.AuditReportRowDTO;
import cv.igrp.platform.access_management.security_audit.domain.entities.SecurityAuditLogEntity;
import cv.igrp.platform.access_management.security_audit.infrastructure.persistence.SecurityAuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Handles {@link GetAuditReportQuery} — the Audit Report list. Delegates filter
 * translation to {@link ReportSpecifications} and projects each matched row into
 * {@link AuditReportRowDTO} via {@link ReportRowMapper} (device derived from
 * user_agent).
 */
@Component
public class GetAuditReportQueryHandler
        implements QueryHandler<GetAuditReportQuery, ResponseEntity<Page<AuditReportRowDTO>>> {

    private final SecurityAuditLogRepository repository;

    public GetAuditReportQueryHandler(SecurityAuditLogRepository repository) {
        this.repository = repository;
    }

    @Override
    @IgrpQueryHandler
    public ResponseEntity<Page<AuditReportRowDTO>> handle(GetAuditReportQuery query) {
        Specification<SecurityAuditLogEntity> spec = ReportSpecifications.audit(
                query.getStartDate(), query.getEndDate(),
                query.getUsername(), query.getModule(), query.getAccessRole(),
                query.getOperationState(), query.getAuthorizedBy(), query.getStatus());

        Page<AuditReportRowDTO> page = repository.findAll(spec, query.getPageable())
                .map(ReportRowMapper::toAuditRow);
        return ResponseEntity.ok(page);
    }
}
