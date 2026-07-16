package cv.igrp.platform.access_management.security_audit.application.queries;

import cv.igrp.platform.access_management.security_audit.application.dto.AccessReportRowDTO;
import cv.igrp.platform.access_management.security_audit.application.dto.AuditReportRowDTO;
import cv.igrp.platform.access_management.security_audit.application.dto.SettingsReportRowDTO;
import cv.igrp.platform.access_management.security_audit.application.support.UserAgentParser;
import cv.igrp.platform.access_management.security_audit.domain.entities.SecurityAuditLogEntity;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Projects {@link SecurityAuditLogEntity} rows onto the three report DTOs.
 * Shared by the paginated JSON query handlers (Audit / Access / Settings) and
 * the Phase 4 export path so a row renders identically whether it is returned
 * as JSON or streamed into a PDF/Excel document.
 */
public final class ReportRowMapper {

    private ReportRowMapper() {
    }

    public static AuditReportRowDTO toAuditRow(SecurityAuditLogEntity e) {
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

    public static AccessReportRowDTO toAccessRow(SecurityAuditLogEntity e) {
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

    public static SettingsReportRowDTO toSettingsRow(SecurityAuditLogEntity e) {
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

    /** The audit {@code timestamp} column is a {@code LocalDateTime} (system zone). */
    static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant();
    }
}
