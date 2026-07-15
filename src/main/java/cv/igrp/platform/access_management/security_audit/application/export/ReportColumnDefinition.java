package cv.igrp.platform.access_management.security_audit.application.export;

import java.util.function.Function;

/**
 * One column of an exported report: a header {@code label} and an {@code value}
 * extractor that renders a row of type {@code T} to a display string. Shared by
 * the {@code ExcelReportExporter} and {@code PdfReportExporter} so both formats
 * emit identical columns in the same order (plan.md §Phase 4.2).
 *
 * <p>The extractor must never return {@code null} — use an empty string for
 * absent values so downstream cells/table entries stay well-formed.
 *
 * @param label the header text
 * @param value renders a row to its cell string (never {@code null})
 */
public record ReportColumnDefinition<T>(String label, Function<T, String> value) {

    /** Convenience factory mirroring the record canonical constructor. */
    public static <T> ReportColumnDefinition<T> of(String label, Function<T, String> value) {
        return new ReportColumnDefinition<>(label, value);
    }
}
