package cv.igrp.platform.access_management.security_audit.application.export;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.Stream;

/**
 * Writes a report as an {@code .xlsx} document.
 *
 * <p>Prefers POI's {@link SXSSFWorkbook} — a sliding in-memory window of rows,
 * the rest flushed to a temp file — which is what keeps heap flat on large
 * exports (requirements.md R7.3 / N2). SXSSF needs a writable scratch directory
 * and creates its temp file as soon as the sheet is created, so where none
 * exists (read-only container filesystem) this falls back to the fully
 * in-memory {@link XSSFWorkbook} rather than failing the request.
 *
 * <p>The choice is made before any byte is written to {@code out}, so the
 * fallback cannot leave a half-written response behind.
 */
@Component
public class ExcelReportExporter {

    private static final Logger log = LoggerFactory.getLogger(ExcelReportExporter.class);

    /** Rows kept in memory before POI flushes older rows to disk (SXSSF only). */
    private static final int WINDOW_SIZE = 100;

    private static final String SHEET_NAME = "Report";

    private final ExcelTempDirStatus tempDirStatus;

    public ExcelReportExporter(ExcelTempDirStatus tempDirStatus) {
        this.tempDirStatus = tempDirStatus;
    }

    /**
     * Writes {@code rows} as a single-sheet workbook with a bold header row.
     * The {@code rows} stream is fully consumed and closed.
     */
    public <T> void write(OutputStream out, List<ReportColumnDefinition<T>> columns, Stream<T> rows) {
        try (Workbook workbook = createWorkbook();
             Stream<T> data = rows) {

            Sheet sheet = workbook.getSheetAt(0);
            CellStyle headerStyle = headerStyle(workbook);

            Row header = sheet.createRow(0);
            for (int c = 0; c < columns.size(); c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(columns.get(c).label());
                cell.setCellStyle(headerStyle);
            }

            int[] rowNum = {1};
            data.forEach(row -> {
                Row sheetRow = sheet.createRow(rowNum[0]++);
                for (int c = 0; c < columns.size(); c++) {
                    sheetRow.createCell(c).setCellValue(columns.get(c).value().apply(row));
                }
            });

            workbook.write(out);
            if (workbook instanceof SXSSFWorkbook streaming) {
                // Remove the temp files backing the streamed rows.
                streaming.dispose();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write Excel report", e);
        }
    }

    /**
     * Creates the workbook and its sheet — the point at which SXSSF materializes
     * its temp file, and therefore the point at which an unwritable scratch
     * directory surfaces. Falls back to in-memory generation if it does.
     */
    private Workbook createWorkbook() {
        if (tempDirStatus.usable()) {
            try {
                SXSSFWorkbook workbook = new SXSSFWorkbook(WINDOW_SIZE);
                workbook.createSheet(SHEET_NAME);
                return workbook;
            } catch (RuntimeException e) {
                log.warn("SXSSF could not create its temp file in '{}' — falling back to in-memory .xlsx "
                        + "generation for this export.", tempDirStatus.dir(), e);
            }
        }
        XSSFWorkbook workbook = new XSSFWorkbook();
        workbook.createSheet(SHEET_NAME);
        return workbook;
    }

    private static CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }
}
