package cv.igrp.platform.access_management.security_audit.application.queries;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.security_audit.application.dto.SettingsReportRowDTO;
import cv.igrp.platform.access_management.security_audit.domain.entities.SecurityAuditLogEntity;
import cv.igrp.platform.access_management.security_audit.infrastructure.persistence.SecurityAuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Handles {@link GetSettingsReportQuery} — the Settings (Configuration) Report.
 * Restricted to rows that carry a {@code settings_area} (i.e. administrative
 * configuration events emitted by Phase 3 instrumentation).
 */
@Component
public class GetSettingsReportQueryHandler
        implements QueryHandler<GetSettingsReportQuery, ResponseEntity<Page<SettingsReportRowDTO>>> {

    private final SecurityAuditLogRepository repository;

    public GetSettingsReportQueryHandler(SecurityAuditLogRepository repository) {
        this.repository = repository;
    }

    @Override
    @IgrpQueryHandler
    public ResponseEntity<Page<SettingsReportRowDTO>> handle(GetSettingsReportQuery query) {
        Specification<SecurityAuditLogEntity> spec = ReportSpecifications.settings(
                query.getStartDate(), query.getEndDate(),
                query.getPerformedBy(), query.getArea(), query.getEntityType(),
                query.getOperation(), query.getEntityName());

        Page<SettingsReportRowDTO> page = repository.findAll(spec, query.getPageable())
                .map(ReportRowMapper::toSettingsRow);
        return ResponseEntity.ok(page);
    }
}
