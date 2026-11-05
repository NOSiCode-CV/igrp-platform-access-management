package cv.igrp.platform.access_management.security_audit.application.export;

import cv.igrp.platform.access_management.security_audit.application.dto.AuditReportRowDTO;
import cv.igrp.platform.access_management.security_audit.domain.enums.ReportFormat;
import cv.igrp.platform.access_management.shared.i18n.I18nTestSupport;
import cv.igrp.platform.access_management.shared.i18n.IgrpLocale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.i18n.LocaleContextHolder;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** BE-8 (FR-19, FR-22): report labels and the "generated on" header follow the requested locale. */
class ReportRendererTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 16);

    private final ExcelReportExporter excel = mock(ExcelReportExporter.class);
    private final PdfReportExporter pdf = mock(PdfReportExporter.class);
    private final CsvReportExporter csv = mock(CsvReportExporter.class);
    private final ReportRenderer renderer = new ReportRenderer(excel, pdf, csv, I18nTestSupport.messageSource());

    @AfterEach
    void reset() {
        LocaleContextHolder.resetLocaleContext();
        LocaleContextHolder.setDefaultLocale(null);
    }

    private static String regionDate(IgrpLocale locale) {
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale.region()).format(DATE);
    }

    @Test
    void headerInPortugueseUsesPtCvRegion() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("pt"));
        assertThat(renderer.pdfHeader(ReportColumns.AUDIT, DATE))
                .isEqualTo("Relatório de Auditoria gerado em " + regionDate(IgrpLocale.PT))
                .contains("16")
                .doesNotContain("generated on");
    }

    @Test
    void headerInEnglishUsesEnGbRegion() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        assertThat(renderer.pdfHeader(ReportColumns.AUDIT, DATE))
                .isEqualTo("Audit Report generated on " + regionDate(IgrpLocale.EN));
    }

    @Test
    void headerInFrenchUsesFrFrRegion() {
        LocaleContextHolder.setLocale(Locale.FRENCH);
        assertThat(renderer.pdfHeader(ReportColumns.AUDIT, DATE))
                .isEqualTo("Rapport d'audit généré le " + regionDate(IgrpLocale.FR));
    }

    @Test
    void untranslatedTitleFallsBackToEnglishButKeepsTheLocalizedSentence() {
        LocaleContextHolder.setLocale(Locale.FRENCH);
        assertThat(renderer.pdfHeader(ReportColumns.ACCESS, DATE))
                .isEqualTo("Access Report généré le " + regionDate(IgrpLocale.FR));
    }

    @Test
    void unsupportedLocaleNeverUsesItsOwnFormatting() {
        LocaleContextHolder.setLocale(Locale.GERMAN);
        assertThat(renderer.pdfHeader(ReportColumns.AUDIT, DATE))
                .isEqualTo("Audit Report generated on " + regionDate(IgrpLocale.EN));
    }

    @Test
    void columnHeadersFollowTheLocaleWithEnglishFallback() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("pt"));
        List<String> labels = renderer.localizedColumns(ReportColumns.AUDIT).stream()
                .map(ReportColumnDefinition::label).toList();
        assertThat(labels).contains("Data de início", "Utilizador", "Estado")
                // report.audit.column.id is not translated to pt -> English default
                .startsWith("ID");

        LocaleContextHolder.setLocale(Locale.ENGLISH);
        assertThat(renderer.localizedColumns(ReportColumns.AUDIT).get(1).label()).isEqualTo("Start Date");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void renderPassesLocalizedColumnsToEveryExporter() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("pt-CV"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        renderer.render(out, ReportColumns.AUDIT, ReportFormat.CSV, Stream.<AuditReportRowDTO>empty());
        ArgumentCaptor<List> csvColumns = ArgumentCaptor.forClass(List.class);
        verify(csv).write(eq(out), csvColumns.capture(), any());
        assertThat(((List<ReportColumnDefinition<?>>) csvColumns.getValue()).get(1).label()).isEqualTo("Data de início");

        renderer.render(out, ReportColumns.AUDIT, ReportFormat.PDF, Stream.<AuditReportRowDTO>empty());
        ArgumentCaptor<String> header = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List> pdfColumns = ArgumentCaptor.forClass(List.class);
        verify(pdf).write(eq(out), header.capture(), pdfColumns.capture(), any());
        assertThat(header.getValue()).startsWith("Relatório de Auditoria gerado em ");
        assertThat(((List<ReportColumnDefinition<?>>) pdfColumns.getValue()).get(1).label()).isEqualTo("Data de início");
    }

    @Test
    void fileNameStaysLocaleNeutral() {
        LocaleContextHolder.setLocale(Locale.FRENCH);
        assertThat(renderer.fileName(ReportColumns.AUDIT, ReportFormat.PDF)).matches("audit-report-\\d{4}-\\d{2}-\\d{2}\\.pdf");
    }
}
