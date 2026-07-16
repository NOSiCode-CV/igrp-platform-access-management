package cv.igrp.platform.access_management.security_audit.application.export;

import cv.igrp.platform.access_management.security_audit.application.dto.AuditReportRowDTO;
import cv.igrp.platform.access_management.security_audit.application.dto.SettingsReportRowDTO;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditStatus;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden-file test for {@link CsvReportExporter}: a fixed 5-row dataset renders
 * to a header line plus one line per row, with RFC 4180 escaping.
 */
class CsvReportExporterTest {

    private final CsvReportExporter exporter = new CsvReportExporter();

    private static AuditReportRowDTO row(int i) {
        AuditReportRowDTO dto = new AuditReportRowDTO();
        dto.setId("id-" + i);
        dto.setStartDate(Instant.parse("2026-06-0" + (i + 1) + "T08:00:00Z"));
        dto.setEndDate(Instant.parse("2026-06-0" + (i + 1) + "T09:00:00Z"));
        dto.setUsername("user" + i);
        dto.setModule("Module" + i);
        dto.setAccessRole("Role" + i);
        dto.setOperationState("State" + i);
        dto.setIpAddress("10.0.0." + i);
        dto.setDevice("Chrome/Windows");
        dto.setAuthorizedBy("admin" + i);
        dto.setStatus(AuditStatus.SUCCESS);
        return dto;
    }

    private static String export(List<AuditReportRowDTO> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new CsvReportExporter().write(out, ReportColumns.AUDIT.columns(), rows.stream());
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void writesBomHeaderLineAndOneLinePerRow() {
        List<AuditReportRowDTO> rows = IntStream.range(0, 5).mapToObj(CsvReportExporterTest::row).toList();

        String csv = export(rows);

        assertThat(csv).startsWith("﻿"); // UTF-8 BOM so Excel reads UTF-8
        String[] lines = csv.replace("﻿", "").split("\r\n");
        assertThat(lines).hasSize(6); // header + 5 rows

        assertThat(lines[0]).isEqualTo(
                "ID,Start Date,End Date,Username,Module,Access Role,Operation State,"
                        + "IP Address,Device,Authorized By,Status");

        for (int i = 0; i < 5; i++) {
            String[] cells = lines[i + 1].split(",");
            assertThat(cells[0]).isEqualTo("id-" + i);
            assertThat(cells[3]).isEqualTo("user" + i);
            assertThat(cells[10]).isEqualTo("SUCCESS");
        }
    }

    @Test
    void quotesFieldsContainingSeparatorsQuotesAndNewlines() {
        SettingsReportRowDTO dto = new SettingsReportRowDTO();
        dto.setPerformedBy("someone");
        dto.setEntityName("name, with comma");
        dto.setPreviousValue("he said \"hi\"");
        dto.setNewValue("line1\nline2");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.write(out, ReportColumns.SETTINGS.columns(), List.of(dto).stream());
        String csv = out.toString(StandardCharsets.UTF_8);

        assertThat(csv).contains("\"name, with comma\"");
        assertThat(csv).contains("\"he said \"\"hi\"\"\"");
        assertThat(csv).contains("\"line1\nline2\"");
    }

    @Test
    void rendersEmptyStringForNullValues() {
        // Null DTO fields render as empty cells, never the literal "null".
        // (The "Unknown" device fallback is applied by ReportRowMapper when the
        // DTO is built from an entity, not by the column extractor.)
        AuditReportRowDTO empty = new AuditReportRowDTO();
        String csv = export(List.of(empty));

        String dataLine = csv.replace("﻿", "").split("\r\n")[1];
        assertThat(dataLine).isEqualTo(",,,,,,,,,,"); // 11 empty cells
        assertThat(dataLine).doesNotContain("null");
    }
}
