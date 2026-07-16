package cv.igrp.platform.access_management.security_audit.application.export;

import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

/**
 * Streams a report to an RFC 4180 CSV document. Writes straight to the response
 * {@link OutputStream} one row at a time — no temp files, no workbook model, so
 * memory stays flat at any row count and it cannot fail on a read-only
 * filesystem the way {@code SXSSFWorkbook} does (see {@code ExportTempFileConfig}).
 */
@Component
public class CsvReportExporter {

    /**
     * Excel assumes the platform ANSI codepage when opening a CSV by
     * double-click; the UTF-8 BOM is what makes it read accented Portuguese
     * names correctly. Harmless to other consumers.
     */
    private static final String UTF8_BOM = "﻿";

    private static final String SEPARATOR = ",";
    private static final String LINE_END = "\r\n"; // RFC 4180

    /**
     * Writes {@code rows} as CSV with a header line. The {@code rows} stream is
     * fully consumed and closed.
     */
    public <T> void write(OutputStream out, List<ReportColumnDefinition<T>> columns, Stream<T> rows) {
        Writer writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        try (Stream<T> data = rows) {
            writer.write(UTF8_BOM);
            writeLine(writer, columns.stream().map(ReportColumnDefinition::label).toList());

            data.forEach(row -> {
                List<String> values = columns.stream().map(c -> c.value().apply(row)).toList();
                try {
                    writeLine(writer, values);
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to write CSV row", e);
                }
            });

            writer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write CSV report", e);
        }
    }

    private static void writeLine(Writer writer, List<String> values) throws IOException {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                line.append(SEPARATOR);
            }
            line.append(escape(values.get(i)));
        }
        writer.write(line.append(LINE_END).toString());
    }

    /**
     * RFC 4180 escaping: quote any field containing a separator, quote or line
     * break, and double the embedded quotes.
     *
     * <p>Note this is not a defence against spreadsheet formula injection — a
     * field like {@code =1+1} round-trips as-is, and quoting does not stop Excel
     * evaluating it on open. Audit rows do carry user-influenced text
     * ({@code entityName}, {@code previousValue}, {@code newValue}). Neutralising
     * it means prefixing with {@code '}, which mutates the data for programmatic
     * consumers, so it is left as a deliberate product decision rather than a
     * silent transform.
     */
    static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        boolean needsQuoting = value.contains(SEPARATOR)
                || value.contains("\"")
                || value.contains("\n")
                || value.contains("\r");

        if (!needsQuoting) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
