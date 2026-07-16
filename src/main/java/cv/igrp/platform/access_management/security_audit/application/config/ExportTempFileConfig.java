package cv.igrp.platform.access_management.security_audit.application.config;

import cv.igrp.platform.access_management.security_audit.application.export.ExcelTempDirStatus;
import org.apache.poi.util.DefaultTempFileCreationStrategy;
import org.apache.poi.util.TempFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Decides, once at startup, whether Apache POI has a writable scratch directory
 * and points POI's temp-file machinery at it.
 *
 * <p>Why this exists: {@code SXSSFWorkbook} streams rows through a temp file
 * ({@code poi-sxssf-sheet*.xml}) that it creates <em>eagerly, when the sheet is
 * created</em> — not only once the 100-row window overflows. So on a container
 * with a read-only root filesystem (or no writable {@code java.io.tmpdir}),
 * every {@code .xlsx} export fails regardless of row count, and it fails inside
 * {@code write()} — after the response headers are already committed, which
 * surfaces to the caller as a bare 500 with correct headers and an empty body.
 * PDF and CSV are unaffected: they render without touching disk.
 *
 * <p>Probing here lets {@code ExcelReportExporter} degrade to in-memory
 * generation instead of failing. Streaming is still preferred when a writable
 * directory exists, since that is what keeps heap flat on large exports.
 * Deployments should provide one — see the {@code /tmp} emptyDir volume in
 * {@code k8s/deployment.yaml}, or point {@code igrp.audit.export.temp-dir} at a
 * mounted writable path.
 */
@Configuration
public class ExportTempFileConfig {

    private static final Logger log = LoggerFactory.getLogger(ExportTempFileConfig.class);

    @Bean
    ExcelTempDirStatus excelTempDirStatus(@Value("${igrp.audit.export.temp-dir:}") String configuredDir) {
        Path dir = resolveDir(configuredDir);
        try {
            Files.createDirectories(dir);
            // createDirectories succeeds on an existing read-only directory, so
            // actually write something to prove the mount is usable.
            Path probe = Files.createTempFile(dir, "igrp-export-probe", ".tmp");
            Files.deleteIfExists(probe);

            TempFile.setTempFileCreationStrategy(new DefaultTempFileCreationStrategy(dir.toFile()));
            log.info("Excel export temp directory ready ({}) — .xlsx exports will stream via SXSSF.", dir);
            return new ExcelTempDirStatus(true, dir);
        } catch (IOException | RuntimeException e) {
            log.warn("Excel export temp directory '{}' is not writable — falling back to in-memory .xlsx "
                    + "generation, which uses more heap on large exports. Mount a writable volume there "
                    + "(see k8s/deployment.yaml) or set 'igrp.audit.export.temp-dir' to a writable path.", dir, e);
            return new ExcelTempDirStatus(false, dir);
        }
    }

    private static Path resolveDir(String configuredDir) {
        if (configuredDir != null && !configuredDir.isBlank()) {
            return Paths.get(configuredDir.trim());
        }
        return Paths.get(System.getProperty("java.io.tmpdir"), "igrp-audit-exports");
    }
}
