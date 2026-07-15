package cv.igrp.platform.access_management.security_audit.application.queries;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.security_audit.application.dto.SettingsReportRowDTO;
import cv.igrp.platform.access_management.security_audit.domain.entities.SecurityAuditLogEntity;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsArea;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsEntityType;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsOperation;
import cv.igrp.platform.access_management.security_audit.infrastructure.persistence.SecurityAuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

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
        Specification<SecurityAuditLogEntity> spec = SecurityAuditSpecificationBuilder.create()
                .fieldIsNotNull("settingsArea")
                .timestampBetween(query.getStartDate(), query.getEndDate())
                .likeIgnoreCase("username", query.getPerformedBy())
                .enumEquals("settingsArea", SettingsArea.class, query.getArea())
                .enumEquals("settingsEntityType", SettingsEntityType.class, query.getEntityType())
                .enumEquals("settingsOperation", SettingsOperation.class, query.getOperation())
                .likeIgnoreCase("entityName", query.getEntityName())
                .build();

        Page<SettingsReportRowDTO> page = repository.findAll(spec, query.getPageable()).map(this::toDTO);
        return ResponseEntity.ok(page);
    }

    private SettingsReportRowDTO toDTO(SecurityAuditLogEntity e) {
        SettingsReportRowDTO dto = new SettingsReportRowDTO();
        dto.setTimestamp(toInstant(e.getTimestamp()));
        dto.setPerformedBy(e.getUsername());
        dto.setArea(e.getSettingsArea());
        dto.setEntityType(e.getSettingsEntityType());
        dto.setOperation(e.getSettingsOperation());
        dto.setEntityName(e.getEntityName());
        dto.setRelatedEntity(e.getRelatedEntity());
        dto.setPreviousValue(e.getPreviousValue());
        dto.setNewValue(e.getNewValue());
        dto.setIpAddress(e.getIpAddress());
        dto.setStatus(e.getStatus());
        return dto;
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant();
    }
}
