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
 * <p>{@code labelKey} is the optional {@code MessageSource} key of the header;
 * {@code ReportRenderer} resolves it in the requested locale before rendering,
 * falling back to {@code label} (English) when unresolved.
 *
 * @param label    the header text (English default)
 * @param value    renders a row to its cell string (never {@code null})
 * @param labelKey i18n key of the header, e.g. {@code report.audit.column.startDate}; may be {@code null}
 */
public record ReportColumnDefinition<T>(String label, Function<T, String> value, String labelKey) {

    /** Column with a fixed (non-localized) header. */
    public static <T> ReportColumnDefinition<T> of(String label, Function<T, String> value) {
        return new ReportColumnDefinition<>(label, value, null);
    }

    /** Column whose header is resolved from {@code labelKey}, defaulting to {@code label}. */
    public static <T> ReportColumnDefinition<T> of(String labelKey, String label, Function<T, String> value) {
        return new ReportColumnDefinition<>(label, value, labelKey);
    }

    /** Copy of this column with another header text (same extractor and key). */
    public ReportColumnDefinition<T> withLabel(String newLabel) {
        return new ReportColumnDefinition<>(newLabel, value, labelKey);
    }
}
