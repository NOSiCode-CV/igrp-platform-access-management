package cv.igrp.platform.access_management.security_audit.application.export;

import cv.igrp.platform.access_management.security_audit.domain.enums.ReportFormat;
import cv.igrp.platform.access_management.shared.i18n.IgrpLocale;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.stream.Stream;

/**
 * Renders a report in a requested {@link ReportFormat}, resolving the format to
 * its exporter and owning the document naming/title rules.
 *
 * <p>Shared by the direct-download endpoints (which render straight to the
 * response) and the archive endpoints (which render to a buffer before
 * uploading), so a report looks identical whichever route produced it.
 *
 * <p><b>i18n (FR-19):</b> the title, the "generated on" header and the column
 * headers are resolved from {@code report.*} keys in the requested locale
 * ({@link LocaleContextHolder}, normalized to a platform language), falling back
 * to the English defaults carried by {@link ReportColumns}. The header date is
 * formatted in the language's fixed region ({@link IgrpLocale#region()}). Cell
 * values and file names stay locale-neutral.
 */
@Component
public class ReportRenderer {

    private final ExcelReportExporter excelExporter;
    private final PdfReportExporter pdfExporter;
    private final CsvReportExporter csvExporter;
    private final MessageSource messageSource;

    public ReportRenderer(ExcelReportExporter excelExporter,
                          PdfReportExporter pdfExporter,
                          CsvReportExporter csvExporter,
                          MessageSource messageSource) {
        this.excelExporter = excelExporter;
        this.pdfExporter = pdfExporter;
        this.csvExporter = csvExporter;
        this.messageSource = messageSource;
    }

    /** Writes {@code rows} to {@code out} in {@code format}. The stream is consumed and closed. */
    public <T> void render(OutputStream out, ReportDescriptor<T> descriptor,
                           ReportFormat format, Stream<T> rows) {
        List<ReportColumnDefinition<T>> columns = localizedColumns(descriptor);
        switch (format) {
            case XLSX -> excelExporter.write(out, columns, rows);
            case CSV -> csvExporter.write(out, columns, rows);
            case PDF -> pdfExporter.write(out, pdfHeader(descriptor), columns, rows);
        }
    }

    /**
     * e.g. {@code "Audit Report generated on 16 Jul 2026"} (en) or
     * {@code "Relatório de Auditoria gerado em 16/07/2026"} (pt) (R7.4).
     */
    public String pdfHeader(ReportDescriptor<?> descriptor) {
        return pdfHeader(descriptor, LocalDate.now());
    }

    String pdfHeader(ReportDescriptor<?> descriptor, LocalDate date) {
        IgrpLocale locale = currentLocale();
        String formattedDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(locale.region())
                .format(date);
        String title = title(descriptor);
        return messageSource.getMessage("report.generatedOn", new Object[]{title, formattedDate},
                title + " generated on " + formattedDate, locale.toLocale());
    }

    /** Column list with headers resolved in the requested locale (English default when unresolved). */
    <T> List<ReportColumnDefinition<T>> localizedColumns(ReportDescriptor<T> descriptor) {
        IgrpLocale locale = currentLocale();
        return descriptor.columns().stream()
                .map(column -> column.labelKey() == null
                        ? column
                        : column.withLabel(messageSource.getMessage(
                                column.labelKey(), null, column.label(), locale.toLocale())))
                .toList();
    }

    /** e.g. {@code "audit-report-2026-07-16.xlsx"}. */
    public String fileName(ReportDescriptor<?> descriptor, ReportFormat format) {
        return descriptor.filenamePrefix() + "-" + LocalDate.now() + "." + format.extension();
    }

    private String title(ReportDescriptor<?> descriptor) {
        return messageSource.getMessage(
                descriptor.titleMessageKey(), null, descriptor.defaultTitle(),
                currentLocale().toLocale());
    }

    /**
     * The requested platform language. Outside a request (e.g. an async archive
     * job) {@link LocaleContextHolder} yields the platform default bound at startup
     * by {@code I18nConfig}; an unsupported locale falls back to the module default
     * (the complete English bundle), never to the JVM locale.
     */
    private static IgrpLocale currentLocale() {
        return IgrpLocale.fromLocale(LocaleContextHolder.getLocale()).orElse(IgrpLocale.EN);
    }
}
