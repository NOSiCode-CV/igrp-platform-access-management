package cv.igrp.platform.access_management.security_audit.application.export;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import cv.igrp.platform.access_management.security_audit.application.dto.AuditReportRowDTO;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditStatus;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden-file test for {@link PdfReportExporter}: render a PDF from a fixed 5-row
 * dataset, extract its text with OpenPDF's {@link PdfTextExtractor} and assert
 * the header line and that every username appears (validation.md §Phase 4).
 */
class PdfReportExporterTest {

    private final PdfReportExporter exporter = new PdfReportExporter();

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
    void writesHeaderLineAndAllUsernames() throws Exception {
        List<AuditReportRowDTO> rows = IntStream.range(0, 5).mapToObj(PdfReportExporterTest::row).toList();
        String headerText = "Audit Report generated on 2026-07-15";

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.write(out, headerText, ReportColumns.AUDIT.columns(), rows.stream());

        PdfReader reader = new PdfReader(out.toByteArray());
        StringBuilder text = new StringBuilder();
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        for (int page = 1; page <= reader.getNumberOfPages(); page++) {
            text.append(extractor.getTextFromPage(page)).append('\n');
        }
        reader.close();

        String content = text.toString();
        assertThat(content).containsPattern("Audit Report generated on \\d{4}-\\d{2}-\\d{2}");
        for (int i = 0; i < 5; i++) {
            assertThat(content).contains("user" + i);
        }
    }
}
