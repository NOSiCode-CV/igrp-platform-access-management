package cv.igrp.platform.access_management.security_audit.domain.enums;

/** Export formats offered for each report, and the archive record's format. */
public enum ReportFormat {

    PDF("pdf", "application/pdf"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    /** Charset is explicit: the payload is UTF-8 (with BOM), and CSV has no in-band encoding declaration. */
    CSV("csv", "text/csv;charset=UTF-8");

    private final String extension;
    private final String contentType;

    ReportFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }
}
