package cv.igrp.platform.access_management.security_audit.application.queries;

import cv.igrp.platform.access_management.security_audit.domain.entities.SecurityAuditLogEntity;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditStatus;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsArea;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsEntityType;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsOperation;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

/**
 * Central factory for the {@link Specification}s backing the three reports.
 * Both the paginated JSON query handlers and the Phase 4 export path build their
 * predicates here, so a report's export honours the exact same filter contract
 * as its JSON sibling (requirements.md R7.2).
 */
public final class ReportSpecifications {

    private ReportSpecifications() {
    }

    public static Specification<SecurityAuditLogEntity> audit(
            LocalDateTime startDate, LocalDateTime endDate,
            String username, String module, String accessRole,
            String operationState, String authorizedBy, String status) {
        return SecurityAuditSpecificationBuilder.create()
                .timestampBetween(startDate, endDate)
                .likeIgnoreCase("username", username)
                .likeIgnoreCase("applicationModule", module)
                .likeIgnoreCase("accessRole", accessRole)
                .likeIgnoreCase("operationState", operationState)
                .likeIgnoreCase("authorizedBy", authorizedBy)
                .enumEquals("status", AuditStatus.class, status)
                .build();
    }

    public static Specification<SecurityAuditLogEntity> access(
            LocalDateTime startDate, LocalDateTime endDate,
            String username, String role, String module, String action, String status) {
        return SecurityAuditSpecificationBuilder.create()
                .timestampBetween(startDate, endDate)
                .likeIgnoreCase("username", username)
                .likeIgnoreCase("accessRole", role)
                .likeIgnoreCase("applicationModule", module)
                .likeIgnoreCase("action", action)
                .enumEquals("status", AuditStatus.class, status)
                .build();
    }

    public static Specification<SecurityAuditLogEntity> settings(
            LocalDateTime startDate, LocalDateTime endDate,
            String performedBy, String area, String entityType,
            String operation, String entityName) {
        return SecurityAuditSpecificationBuilder.create()
                .fieldIsNotNull("settingsArea")
                .timestampBetween(startDate, endDate)
                .likeIgnoreCase("username", performedBy)
                .enumEquals("settingsArea", SettingsArea.class, area)
                .enumEquals("settingsEntityType", SettingsEntityType.class, entityType)
                .enumEquals("settingsOperation", SettingsOperation.class, operation)
                .likeIgnoreCase("entityName", entityName)
                .build();
    }
}
