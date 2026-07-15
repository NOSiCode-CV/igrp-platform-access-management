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
                    ReportColumnDefinition.of("ID", AuditReportRowDTO::getId),
                    ReportColumnDefinition.of("Start Date", r -> formatInstant(r.getStartDate())),
                    ReportColumnDefinition.of("End Date", r -> formatInstant(r.getEndDate())),
                    ReportColumnDefinition.of("Username", r -> nullToEmpty(r.getUsername())),
                    ReportColumnDefinition.of("Module", r -> nullToEmpty(r.getModule())),
                    ReportColumnDefinition.of("Access Role", r -> nullToEmpty(r.getAccessRole())),
                    ReportColumnDefinition.of("Operation State", r -> nullToEmpty(r.getOperationState())),
                    ReportColumnDefinition.of("IP Address", r -> nullToEmpty(r.getIpAddress())),
                    ReportColumnDefinition.of("Device", r -> nullToEmpty(r.getDevice())),
                    ReportColumnDefinition.of("Authorized By", r -> nullToEmpty(r.getAuthorizedBy())),
                    ReportColumnDefinition.of("Status", r -> enumName(r.getStatus()))),
            "report.audit.title", "Audit Report", "audit-report");

    public static final ReportDescriptor<AccessReportRowDTO> ACCESS = new ReportDescriptor<>(
            List.of(
                    ReportColumnDefinition.of("Timestamp", r -> formatInstant(r.getTimestamp())),
                    ReportColumnDefinition.of("Username", r -> nullToEmpty(r.getUsername())),
                    ReportColumnDefinition.of("Role", r -> nullToEmpty(r.getRole())),
                    ReportColumnDefinition.of("Module", r -> nullToEmpty(r.getModule())),
                    ReportColumnDefinition.of("Action", r -> nullToEmpty(r.getAction())),
                    ReportColumnDefinition.of("IP Address", r -> nullToEmpty(r.getIpAddress())),
                    ReportColumnDefinition.of("Status", r -> enumName(r.getStatus()))),
            "report.access.title", "Access Report", "access-report");

    public static final ReportDescriptor<SettingsReportRowDTO> SETTINGS = new ReportDescriptor<>(
            List.of(
                    ReportColumnDefinition.of("Timestamp", r -> formatInstant(r.getTimestamp())),
                    ReportColumnDefinition.of("Performed By", r -> nullToEmpty(r.getPerformedBy())),
                    ReportColumnDefinition.of("Area", r -> enumName(r.getArea())),
                    ReportColumnDefinition.of("Entity Type", r -> enumName(r.getEntityType())),
                    ReportColumnDefinition.of("Operation", r -> enumName(r.getOperation())),
                    ReportColumnDefinition.of("Entity Name", r -> nullToEmpty(r.getEntityName())),
                    ReportColumnDefinition.of("Related Entity", r -> nullToEmpty(r.getRelatedEntity())),
                    ReportColumnDefinition.of("Previous Value", r -> nullToEmpty(r.getPreviousValue())),
                    ReportColumnDefinition.of("New Value", r -> nullToEmpty(r.getNewValue())),
                    ReportColumnDefinition.of("IP Address", r -> nullToEmpty(r.getIpAddress())),
                    ReportColumnDefinition.of("Status", r -> enumName(r.getStatus()))),
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
