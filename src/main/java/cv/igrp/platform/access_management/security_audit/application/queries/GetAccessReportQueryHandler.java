package cv.igrp.platform.access_management.security_audit.application.queries;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.security_audit.application.dto.AccessReportRowDTO;
import cv.igrp.platform.access_management.security_audit.domain.entities.SecurityAuditLogEntity;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditStatus;
import cv.igrp.platform.access_management.security_audit.infrastructure.persistence.SecurityAuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

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
        Specification<SecurityAuditLogEntity> spec = SecurityAuditSpecificationBuilder.create()
                .timestampBetween(query.getStartDate(), query.getEndDate())
                .likeIgnoreCase("username", query.getUsername())
                .likeIgnoreCase("accessRole", query.getRole())
                .likeIgnoreCase("applicationModule", query.getModule())
                .likeIgnoreCase("action", query.getAction())
                .enumEquals("status", AuditStatus.class, query.getStatus())
                .build();

        Page<AccessReportRowDTO> page = repository.findAll(spec, query.getPageable()).map(this::toDTO);
        return ResponseEntity.ok(page);
    }

    private AccessReportRowDTO toDTO(SecurityAuditLogEntity e) {
        AccessReportRowDTO dto = new AccessReportRowDTO();
        dto.setTimestamp(toInstant(e.getTimestamp()));
        dto.setUsername(e.getUsername());
        dto.setRole(e.getAccessRole());
        dto.setModule(e.getApplicationModule());
        dto.setAction(e.getAction());
        dto.setIpAddress(e.getIpAddress());
        dto.setStatus(e.getStatus());
        return dto;
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant();
    }
}
