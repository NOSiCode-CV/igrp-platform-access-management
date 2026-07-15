package cv.igrp.platform.access_management.security_audit.application.export;

import java.util.List;

/**
 * Bundles everything the exporters need to render one report type: the ordered
 * {@link ReportColumnDefinition column list}, the localizable document title and
 * the download filename prefix. Keeping these together lets the controller drive
 * PDF and Excel generation generically over {@code T}.
 *
 * @param columns          ordered columns (header + extractor)
 * @param titleMessageKey  {@code MessageSource} key for the document title
 * @param defaultTitle     fallback title when the key is unresolved
 * @param filenamePrefix   download filename stem, e.g. {@code "audit-report"}
 */
public record ReportDescriptor<T>(
        List<ReportColumnDefinition<T>> columns,
        String titleMessageKey,
        String defaultTitle,
        String filenamePrefix) {
}
