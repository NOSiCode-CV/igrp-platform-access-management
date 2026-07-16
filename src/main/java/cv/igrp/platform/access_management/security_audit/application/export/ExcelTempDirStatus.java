package cv.igrp.platform.access_management.security_audit.application.export;

import java.nio.file.Path;

/**
 * Whether Apache POI has a writable scratch directory, decided once at startup
 * by {@code ExportTempFileConfig}.
 *
 * <p>{@code SXSSFWorkbook} stages rows in a temp file that it creates eagerly
 * when the sheet is created — so on a read-only filesystem every {@code .xlsx}
 * export fails regardless of row count. {@link ExcelReportExporter} consults
 * this to decide between streaming (SXSSF) and in-memory (XSSF) generation.
 *
 * @param usable whether a temp file could actually be created in {@code dir}
 * @param dir    the directory that was probed
 */
public record ExcelTempDirStatus(boolean usable, Path dir) {
}
