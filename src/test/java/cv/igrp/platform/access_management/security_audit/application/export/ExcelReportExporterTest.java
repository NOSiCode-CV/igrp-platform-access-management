package cv.igrp.platform.access_management.security_audit.application.export;

import cv.igrp.platform.access_management.security_audit.application.dto.AuditReportRowDTO;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditStatus;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden-file test for {@link ExcelReportExporter}: generate a workbook from a
 * fixed 5-row dataset, reopen it with POI and assert the header and cell values
 * row-by-row (validation.md §Phase 4 golden-file).
 */
class ExcelReportExporterTest {

    private final ExcelReportExporter exporter = new ExcelReportExporter();

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

    @Test
    void writesHeaderAndFiveDataRows() throws Exception {
        List<AuditReportRowDTO> rows = IntStream.range(0, 5).mapToObj(ExcelReportExporterTest::row).toList();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.write(out, ReportColumns.AUDIT.columns(), rows.stream());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);

            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("ID");
            assertThat(header.getCell(3).getStringCellValue()).isEqualTo("Username");
            assertThat(header.getCell(10).getStringCellValue()).isEqualTo("Status");

            assertThat(sheet.getLastRowNum()).isEqualTo(5); // header + 5 data rows

            for (int i = 0; i < 5; i++) {
                Row dataRow = sheet.getRow(i + 1);
                assertThat(dataRow.getCell(0).getStringCellValue()).isEqualTo("id-" + i);
                assertThat(dataRow.getCell(3).getStringCellValue()).isEqualTo("user" + i);
                assertThat(dataRow.getCell(4).getStringCellValue()).isEqualTo("Module" + i);
                assertThat(dataRow.getCell(9).getStringCellValue()).isEqualTo("admin" + i);
                assertThat(dataRow.getCell(10).getStringCellValue()).isEqualTo("SUCCESS");
            }
        }
    }
}
