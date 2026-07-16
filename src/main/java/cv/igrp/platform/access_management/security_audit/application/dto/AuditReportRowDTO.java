package cv.igrp.platform.access_management.security_audit.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One row of the Audit Report ({@code GET /api/auth/reports/audit}).
 * Field names, casing and nullability match the source spec JSON
 * (requirements.md §1.5.1).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@IgrpDTO
public class AuditReportRowDTO {

    private String id;
    private Instant startDate;
    private Instant endDate;
    private String username;
    private String module;
    private String accessRole;
    private String operationState;
    private String ipAddress;
    /** Derived at read time from {@code user_agent} — see {@code UserAgentParser}. */
    private String device;
    private String authorizedBy;
    private AuditStatus status;
}
