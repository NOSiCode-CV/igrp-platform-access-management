package cv.igrp.platform.access_management.security_audit.application.export;

import cv.igrp.platform.access_management.security_audit.application.dto.AccessReportRowDTO;
import cv.igrp.platform.access_management.security_audit.application.dto.AuditReportRowDTO;
import cv.igrp.platform.access_management.security_audit.application.dto.SettingsReportRowDTO;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Column definitions (and packaging metadata) for the three exportable reports.
 * Column order and labels mirror the JSON report DTOs (requirements.md §1.5) so
 * an exported document reads the same as its on-screen table.
 */
public final class ReportColumns {

    /** Human-readable timestamp rendering shared by every report cell. */
    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private ReportColumns() {
    }

    public static final ReportDescriptor<AuditReportRowDTO> AUDIT = new ReportDescriptor<>(
            List.of(
                    ReportColumnDefinition.of("report.audit.column.id", "ID", AuditReportRowDTO::getId),
                    ReportColumnDefinition.of("report.audit.column.startDate", "Start Date", r -> formatInstant(r.getStartDate())),
                    ReportColumnDefinition.of("report.audit.column.endDate", "End Date", r -> formatInstant(r.getEndDate())),
                    ReportColumnDefinition.of("report.audit.column.username", "Username", r -> nullToEmpty(r.getUsername())),
                    ReportColumnDefinition.of("report.audit.column.module", "Module", r -> nullToEmpty(r.getModule())),
                    ReportColumnDefinition.of("report.audit.column.accessRole", "Access Role", r -> nullToEmpty(r.getAccessRole())),
                    ReportColumnDefinition.of("report.audit.column.operationState", "Operation State", r -> nullToEmpty(r.getOperationState())),
                    ReportColumnDefinition.of("report.audit.column.ipAddress", "IP Address", r -> nullToEmpty(r.getIpAddress())),
                    ReportColumnDefinition.of("report.audit.column.device", "Device", r -> nullToEmpty(r.getDevice())),
                    ReportColumnDefinition.of("report.audit.column.authorizedBy", "Authorized By", r -> nullToEmpty(r.getAuthorizedBy())),
                    ReportColumnDefinition.of("report.audit.column.status", "Status", r -> enumName(r.getStatus()))),
            "report.audit.title", "Audit Report", "audit-report");

    public static final ReportDescriptor<AccessReportRowDTO> ACCESS = new ReportDescriptor<>(
            List.of(
                    ReportColumnDefinition.of("report.access.column.timestamp", "Timestamp", r -> formatInstant(r.getTimestamp())),
                    ReportColumnDefinition.of("report.access.column.username", "Username", r -> nullToEmpty(r.getUsername())),
                    ReportColumnDefinition.of("report.access.column.role", "Role", r -> nullToEmpty(r.getRole())),
                    ReportColumnDefinition.of("report.access.column.module", "Module", r -> nullToEmpty(r.getModule())),
                    ReportColumnDefinition.of("report.access.column.action", "Action", r -> nullToEmpty(r.getAction())),
                    ReportColumnDefinition.of("report.access.column.ipAddress", "IP Address", r -> nullToEmpty(r.getIpAddress())),
                    ReportColumnDefinition.of("report.access.column.status", "Status", r -> enumName(r.getStatus()))),
            "report.access.title", "Access Report", "access-report");

    public static final ReportDescriptor<SettingsReportRowDTO> SETTINGS = new ReportDescriptor<>(
            List.of(
                    ReportColumnDefinition.of("report.settings.column.timestamp", "Timestamp", r -> formatInstant(r.getTimestamp())),
                    ReportColumnDefinition.of("report.settings.column.performedBy", "Performed By", r -> nullToEmpty(r.getPerformedBy())),
                    ReportColumnDefinition.of("report.settings.column.area", "Area", r -> enumName(r.getArea())),
                    ReportColumnDefinition.of("report.settings.column.entityType", "Entity Type", r -> enumName(r.getEntityType())),
                    ReportColumnDefinition.of("report.settings.column.operation", "Operation", r -> enumName(r.getOperation())),
                    ReportColumnDefinition.of("report.settings.column.entityName", "Entity Name", r -> nullToEmpty(r.getEntityName())),
                    ReportColumnDefinition.of("report.settings.column.relatedEntity", "Related Entity", r -> nullToEmpty(r.getRelatedEntity())),
                    ReportColumnDefinition.of("report.settings.column.previousValue", "Previous Value", r -> nullToEmpty(r.getPreviousValue())),
                    ReportColumnDefinition.of("report.settings.column.newValue", "New Value", r -> nullToEmpty(r.getNewValue())),
                    ReportColumnDefinition.of("report.settings.column.ipAddress", "IP Address", r -> nullToEmpty(r.getIpAddress())),
                    ReportColumnDefinition.of("report.settings.column.status", "Status", r -> enumName(r.getStatus()))),
            "report.settings.title", "Settings Report", "settings-report");

    private static String formatInstant(Instant instant) {
        return instant == null ? "" : TS_FORMAT.format(instant);
    }

    private static String enumName(Enum<?> value) {
        return value == null ? "" : value.name();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
