package cv.igrp.platform.access_management.security_audit.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditStatus;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsArea;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsEntityType;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsOperation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One row of the Settings (Configuration) Report
 * ({@code GET /api/auth/reports/settings}). Field names match the source spec
 * JSON (requirements.md §1.5.3). {@code relatedEntity}, {@code previousValue}
 * and {@code newValue} are nullable.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@IgrpDTO
public class SettingsReportRowDTO {

    private Instant timestamp;
    private String performedBy;
    private SettingsArea area;
    private SettingsEntityType entityType;
    private SettingsOperation operation;
    private String entityName;
    private String relatedEntity;
    private String previousValue;
    private String newValue;
    private String ipAddress;
    private AuditStatus status;
}
