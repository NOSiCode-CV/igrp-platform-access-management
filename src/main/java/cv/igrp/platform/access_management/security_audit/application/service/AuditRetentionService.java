package cv.igrp.platform.access_management.security_audit.application.service;

import cv.igrp.platform.access_management.security_audit.application.config.AuditRetentionProperties;
import cv.igrp.platform.access_management.security_audit.domain.entities.SecurityAuditArchiveEntity;
import cv.igrp.platform.access_management.security_audit.domain.entities.SecurityAuditLogEntity;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditCategory;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditEventType;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditStatus;
import cv.igrp.platform.access_management.security_audit.infrastructure.persistence.SecurityAuditArchiveRepository;
import cv.igrp.platform.access_management.security_audit.infrastructure.persistence.SecurityAuditLogRepository;
import cv.igrp.platform.access_management.shared.infrastructure.json.PersistedJson;
import cv.igrp.platform.filemanager.StorageService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Archive-then-delete retention for {@code t_security_audit_log} (v3-23).
 *
 * <p>Rows older than {@code igrp.audit.retention.days} are grouped into runs of
 * consecutive sequence numbers. For each run:
 * <ol>
 *   <li>the rows are written to object storage as JSON lines, hashes included, so
 *       the archive can be checked offline. If the upload fails nothing is
 *       deleted;</li>
 *   <li>in one transaction: an archive record is written, an
 *       {@link AuditEventType#AUDIT_ARCHIVED} checkpoint is appended to the chain,
 *       and the run is deleted. The database refuses the delete for any row that
 *       is not covered by an archive record or is inside the 365-day floor
 *       (V18_1).</li>
 * </ol>
 * The archive record keeps the hashes around the run, so
 * {@link SecurityAuditChainValidator} can bridge the gap the deletion leaves.
 *
 * <p>The cutoff is taken from the database clock, the same clock the V18_1
 * trigger checks, so a skewed JVM clock cannot make every run fail. The storage
 * key depends only on the run, so a retry overwrites the same object instead of
 * leaving a second copy. Replicas may run the job at the same time: the run is
 * committed under the chain lock, and a run another replica has already archived
 * is skipped.
 */
@Service
@EnableConfigurationProperties(AuditRetentionProperties.class)
public class AuditRetentionService {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionService.class);
    private static final String ARCHIVE_FOLDER = "private/audit-archive/";
    private static final String CONTENT_TYPE = "application/x-ndjson";

    private final SecurityAuditLogRepository logs;
    private final SecurityAuditArchiveRepository archives;
    private final SecurityAuditChainService chain;
    private final StorageService storage;
    private final AuditRetentionProperties properties;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Counter failures;

    public AuditRetentionService(SecurityAuditLogRepository logs,
                                 SecurityAuditArchiveRepository archives,
                                 SecurityAuditChainService chain,
                                 StorageService storage,
                                 AuditRetentionProperties properties,
                                 JdbcTemplate jdbc,
                                 TransactionTemplate tx,
                                 MeterRegistry meterRegistry) {
        this.logs = logs;
        this.archives = archives;
        this.chain = chain;
        this.storage = storage;
        this.properties = properties;
        this.jdbc = jdbc;
        this.tx = tx;
        this.failures = Counter.builder(SecurityAuditServiceImpl.WRITE_FAILURES_METRIC)
                .description("Audit rows that failed to be written")
                .tag("path", "retention")
                .register(meterRegistry);
    }

    @Scheduled(cron = "${igrp.audit.retention.cron:0 0 3 * * *}")
    public void scheduledRun() {
        try {
            int archived = applyRetention();
            if (archived > 0) {
                log.info("[AUDIT] Retention archived and deleted {} audit rows.", archived);
            }
        } catch (RuntimeException e) {
            failures.increment();
            log.error("[AUDIT] Audit retention run failed; nothing past the failure was deleted.", e);
        }
    }

    /**
     * Archives and deletes up to {@code batch-size} rows past the retention period.
     *
     * @return the number of rows archived and deleted.
     */
    public int applyRetention() {
        LocalDateTime cutoff = jdbc.queryForObject("SELECT localtimestamp", LocalDateTime.class)
                .minusDays(properties.getDays());
        List<Long> eligible = jdbc.queryForList("""
                SELECT l.sequence_number FROM t_security_audit_log l
                 WHERE l.sequence_number > 0
                   AND l."timestamp" < ?
                   AND NOT EXISTS (SELECT 1 FROM t_security_audit_archive a
                                    WHERE l.sequence_number BETWEEN a.first_sequence AND a.last_sequence)
                 ORDER BY l.sequence_number
                 LIMIT ?""", Long.class, cutoff, properties.getBatchSize());

        int archived = 0;
        for (long[] run : consecutiveRuns(eligible)) {
            archived += archiveRun(run[0], run[1], cutoff);
        }
        return archived;
    }

    private int archiveRun(long first, long last, LocalDateTime cutoff) {
        List<SecurityAuditLogEntity> rows = logs.findBySequenceNumberBetweenOrderBySequenceNumberAsc(first, last);
        String key = ARCHIVE_FOLDER + first + "-" + last + ".jsonl";
        try {
            storage.uploadFile(toJsonLines(rows), key, CONTENT_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("Could not archive audit rows " + first + "-" + last + " to " + key, e);
        }

        Integer deleted = tx.execute(status -> {
            SecurityAuditArchiveEntity archive = new SecurityAuditArchiveEntity(first, last,
                    nz(rows.getFirst().getPreviousHash()), nz(rows.getLast().getCurrentHash()),
                    rows.size(), key, cutoff);
            // Appending takes the chain lock, which serializes this check with
            // the same job on other replicas.
            chain.append(checkpoint(archive));
            if (alreadyArchived(first, last)) {
                status.setRollbackOnly();
                return 0;
            }
            archives.saveAndFlush(archive);
            jdbc.update("DELETE FROM t_security_audit_log WHERE sequence_number BETWEEN ? AND ?", first, last);
            return rows.size();
        });
        return deleted == null ? 0 : deleted;
    }

    private boolean alreadyArchived(long first, long last) {
        Long overlapping = jdbc.queryForObject(
                "SELECT count(*) FROM t_security_audit_archive WHERE first_sequence <= ? AND last_sequence >= ?",
                Long.class, last, first);
        return overlapping != null && overlapping > 0;
    }

    private static SecurityAuditLogEntity checkpoint(SecurityAuditArchiveEntity archive) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("firstSequence", archive.getFirstSequence());
        context.put("lastSequence", archive.getLastSequence());
        context.put("rowCount", archive.getRowCount());
        context.put("lastHash", archive.getLastHash());
        context.put("storageKey", archive.getStorageKey());
        context.put("cutoff", archive.getCutoff().toString());

        SecurityAuditLogEntity row = new SecurityAuditLogEntity();
        row.setEventType(AuditEventType.AUDIT_ARCHIVED);
        row.setCategory(AuditCategory.SYSTEM);
        row.setStatus(AuditStatus.SUCCESS);
        row.setAction("Audit Archived");
        row.setTimestamp(LocalDateTime.now());
        row.setContextData(PersistedJson.MAPPER.writeValueAsString(context));
        return row;
    }

    private static byte[] toJsonLines(List<SecurityAuditLogEntity> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (SecurityAuditLogEntity row : rows) {
            out.writeBytes(PersistedJson.MAPPER.writeValueAsString(row).getBytes(StandardCharsets.UTF_8));
            out.write('\n');
        }
        return out.toByteArray();
    }

    /** Splits ascending sequence numbers into [first, last] runs with no gaps. */
    static List<long[]> consecutiveRuns(List<Long> sequences) {
        List<long[]> runs = new ArrayList<>();
        for (long sequence : sequences) {
            if (!runs.isEmpty() && runs.getLast()[1] + 1 == sequence) {
                runs.getLast()[1] = sequence;
            } else {
                runs.add(new long[]{sequence, sequence});
            }
        }
        return runs;
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
