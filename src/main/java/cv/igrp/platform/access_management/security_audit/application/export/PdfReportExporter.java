package cv.igrp.platform.access_management.security_audit.application.export;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.util.List;
import java.util.stream.Stream;

/**
 * Renders a report to a PDF document with OpenPDF (LGPL fork of iText 4). The
 * first page carries the {@code headerText} title (e.g. "Audit Report generated
 * on 2026-07-15", localized upstream via {@code MessageSource}, R7.4), followed
 * by a bordered table of the report rows.
 *
 * <p>Pages are laid out in landscape to fit the wider reports. Rows are consumed
 * from the stream and added to a single {@link PdfPTable}; PDF exports are capped
 * at report-sized volumes (N3 targets 10k rows).
 */
@Component
public class PdfReportExporter {

    private static final Font HEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
    private static final Font TABLE_HEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
    private static final Font CELL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 8);

    /**
     * Writes {@code rows} as a landscape PDF titled {@code headerText}. The
     * {@code rows} stream is fully consumed and closed.
     */
    public <T> void write(OutputStream out, String headerText,
                          List<ReportColumnDefinition<T>> columns, Stream<T> rows) {
        Document document = new Document(PageSize.A4.rotate());
        try (Stream<T> data = rows) {
            PdfWriter.getInstance(document, out);
            document.open();

            Paragraph title = new Paragraph(headerText, HEADER_FONT);
            title.setSpacingAfter(12f);
            document.add(title);

            PdfPTable table = new PdfPTable(columns.size());
            table.setWidthPercentage(100f);
            table.setHeaderRows(1);
            for (ReportColumnDefinition<T> column : columns) {
                table.addCell(new PdfPCell(new Phrase(column.label(), TABLE_HEADER_FONT)));
            }

            data.forEach(row -> {
                for (ReportColumnDefinition<T> column : columns) {
                    table.addCell(new PdfPCell(new Phrase(column.value().apply(row), CELL_FONT)));
                }
            });

            document.add(table);
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to write PDF report", e);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }
    }
}
