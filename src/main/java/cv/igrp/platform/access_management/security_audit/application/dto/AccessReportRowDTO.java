package cv.igrp.platform.access_management.security_audit.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One row of the Access Report ({@code GET /api/auth/reports/access}).
 * Field names match the source spec JSON (requirements.md §1.5.2).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@IgrpDTO
public class AccessReportRowDTO {

    private Instant timestamp;
    private String username;
    private String role;
    private String module;
    private String action;
    private String ipAddress;
    private AuditStatus status;
}
