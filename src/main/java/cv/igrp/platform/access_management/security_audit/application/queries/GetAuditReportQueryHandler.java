package cv.igrp.platform.access_management.security_audit.application.queries;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.security_audit.application.dto.AuditReportRowDTO;
import cv.igrp.platform.access_management.security_audit.application.support.UserAgentParser;
import cv.igrp.platform.access_management.security_audit.domain.entities.SecurityAuditLogEntity;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditStatus;
import cv.igrp.platform.access_management.security_audit.infrastructure.persistence.SecurityAuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Handles {@link GetAuditReportQuery} — the Audit Report list. Delegates filter
 * translation to {@link SecurityAuditSpecificationBuilder} and projects each
 * matched row into {@link AuditReportRowDTO} (device derived from user_agent).
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
        Specification<SecurityAuditLogEntity> spec = SecurityAuditSpecificationBuilder.create()
                .timestampBetween(query.getStartDate(), query.getEndDate())
                .likeIgnoreCase("username", query.getUsername())
                .likeIgnoreCase("applicationModule", query.getModule())
                .likeIgnoreCase("accessRole", query.getAccessRole())
                .likeIgnoreCase("operationState", query.getOperationState())
                .likeIgnoreCase("authorizedBy", query.getAuthorizedBy())
                .enumEquals("status", AuditStatus.class, query.getStatus())
                .build();

        Page<AuditReportRowDTO> page = repository.findAll(spec, query.getPageable()).map(this::toDTO);
        return ResponseEntity.ok(page);
    }

    private AuditReportRowDTO toDTO(SecurityAuditLogEntity e) {
        AuditReportRowDTO dto = new AuditReportRowDTO();
        dto.setId(e.getId() != null ? e.getId().toString() : null);
        dto.setStartDate(e.getPeriodStart());
        dto.setEndDate(e.getPeriodEnd());
        dto.setUsername(e.getUsername());
        dto.setModule(e.getApplicationModule());
        dto.setAccessRole(e.getAccessRole());
        dto.setOperationState(e.getOperationState());
        dto.setIpAddress(e.getIpAddress());
        dto.setDevice(UserAgentParser.parse(e.getUserAgent()));
        dto.setAuthorizedBy(e.getAuthorizedBy());
        dto.setStatus(e.getStatus());
        return dto;
    }
}
