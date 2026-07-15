package cv.igrp.platform.access_management.security_audit.application.export;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.Stream;

/**
 * Streams a report to an {@code .xlsx} document using POI's
 * {@link SXSSFWorkbook} (a sliding in-memory window of rows, the rest flushed to
 * a temp file) so heap stays flat regardless of row count (requirements.md
 * R7.3 / N2).
 */
@Component
public class ExcelReportExporter {

    /** Rows kept in memory before POI flushes older rows to disk. */
    private static final int WINDOW_SIZE = 100;

    /**
     * Writes {@code rows} as a single-sheet workbook with a bold header row.
     * The {@code rows} stream is fully consumed and closed.
     */
    public <T> void write(OutputStream out, List<ReportColumnDefinition<T>> columns, Stream<T> rows) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(WINDOW_SIZE);
             Stream<T> data = rows) {

            Sheet sheet = workbook.createSheet("Report");
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
            // Remove the temp files backing the streamed rows.
            workbook.dispose();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write Excel report", e);
        }
    }

    private static CellStyle headerStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }
}
