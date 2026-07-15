package cv.igrp.platform.access_management.security_audit.application.queries;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.security_audit.application.dto.AccessReportRowDTO;
import cv.igrp.platform.access_management.security_audit.domain.entities.SecurityAuditLogEntity;
import cv.igrp.platform.access_management.security_audit.infrastructure.persistence.SecurityAuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Handles {@link GetAccessReportQuery} — the Access Report list.
 */
@Component
public class GetAccessReportQueryHandler
        implements QueryHandler<GetAccessReportQuery, ResponseEntity<Page<AccessReportRowDTO>>> {

    private final SecurityAuditLogRepository repository;

    public GetAccessReportQueryHandler(SecurityAuditLogRepository repository) {
        this.repository = repository;
    }

    @Override
    @IgrpQueryHandler
    public ResponseEntity<Page<AccessReportRowDTO>> handle(GetAccessReportQuery query) {
        Specification<SecurityAuditLogEntity> spec = ReportSpecifications.access(
                query.getStartDate(), query.getEndDate(),
                query.getUsername(), query.getRole(), query.getModule(),
                query.getAction(), query.getStatus());

        Page<AccessReportRowDTO> page = repository.findAll(spec, query.getPageable())
                .map(ReportRowMapper::toAccessRow);
        return ResponseEntity.ok(page);
    }
}
