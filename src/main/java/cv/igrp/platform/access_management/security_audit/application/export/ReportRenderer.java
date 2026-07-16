package cv.igrp.platform.access_management.security_audit.application.export;

import cv.igrp.platform.access_management.security_audit.domain.enums.ReportFormat;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.time.LocalDate;
import java.util.stream.Stream;

/**
 * Renders a report in a requested {@link ReportFormat}, resolving the format to
 * its exporter and owning the document naming/title rules.
 *
 * <p>Shared by the direct-download endpoints (which render straight to the
 * response) and the archive endpoints (which render to a buffer before
 * uploading), so a report looks identical whichever route produced it.
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
        switch (format) {
            case XLSX -> excelExporter.write(out, descriptor.columns(), rows);
            case CSV -> csvExporter.write(out, descriptor.columns(), rows);
            case PDF -> pdfExporter.write(out, pdfHeader(descriptor), descriptor.columns(), rows);
        }
    }

    /** e.g. {@code "Audit Report generated on 2026-07-16"} (R7.4). */
    public String pdfHeader(ReportDescriptor<?> descriptor) {
        return title(descriptor) + " generated on " + LocalDate.now();
    }

    /** e.g. {@code "audit-report-2026-07-16.xlsx"}. */
    public String fileName(ReportDescriptor<?> descriptor, ReportFormat format) {
        return descriptor.filenamePrefix() + "-" + LocalDate.now() + "." + format.extension();
    }

    private String title(ReportDescriptor<?> descriptor) {
        return messageSource.getMessage(
                descriptor.titleMessageKey(), null, descriptor.defaultTitle(),
                LocaleContextHolder.getLocale());
    }
}
