package cv.igrp.platform.access_management.security_audit.application.service;

import cv.igrp.platform.access_management.security_audit.application.config.AuditRetentionProperties;
import cv.igrp.platform.access_management.security_audit.domain.entities.SecurityAuditArchiveEntity;
import cv.igrp.platform.access_management.security_audit.domain.entities.SecurityAuditLogEntity;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditEventType;
import cv.igrp.platform.access_management.security_audit.infrastructure.persistence.SecurityAuditArchiveRepository;
import cv.igrp.platform.access_management.security_audit.infrastructure.persistence.SecurityAuditLogRepository;
import cv.igrp.platform.filemanager.StorageService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * v3-23 (#30) — the retention job's decisions: a failure is counted and stops
 * the run before anything is deleted, and a run another replica has archived is
 * skipped. The end-to-end path against Postgres is in {@code AuditRetentionIT}.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class AuditRetentionServiceTest {

    private static final LocalDateTime DB_NOW = LocalDateTime.of(2030, 1, 1, 3, 0);
    private static final String ARCHIVE_COUNT = "SELECT count(*) FROM t_security_audit_archive";

    @Mock private SecurityAuditLogRepository logs;
    @Mock private SecurityAuditArchiveRepository archives;
    @Mock private SecurityAuditChainService chain;
    @Mock private StorageService storage;
    @Mock private JdbcTemplate jdbc;
    @Mock private TransactionTemplate tx;

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final SimpleTransactionStatus status = new SimpleTransactionStatus();
    private AuditRetentionService service;

    @BeforeEach
    void setUp() {
        service = new AuditRetentionService(logs, archives, chain, storage, new AuditRetentionProperties(),
                jdbc, tx, meters);
        lenient().when(jdbc.queryForObject("SELECT localtimestamp", LocalDateTime.class)).thenReturn(DB_NOW);
        lenient().when(tx.execute(any())).thenAnswer(inv ->
                inv.<TransactionCallback<?>>getArgument(0).doInTransaction(status));
    }

    @Test
    void theCutoffIsTheDatabaseClockMinusThePeriod() {
        eligible(List.of());

        service.applyRetention();

        verify(jdbc).queryForList(anyString(), eq(Long.class), eq(DB_NOW.minusDays(1825)), eq(10000));
    }

    @Test
    void aRunIsUploadedThenArchivedThenDeleted() throws Exception {
        eligible(List.of(4L, 5L));
        rows(4, 5);
        notArchivedYet();

        int deleted = service.applyRetention();

        assertThat(deleted).isEqualTo(2);
        verify(storage).uploadFile(any(byte[].class), eq("private/audit-archive/4-5.jsonl"),
                eq("application/x-ndjson"));
        ArgumentCaptor<SecurityAuditLogEntity> checkpoint = ArgumentCaptor.forClass(SecurityAuditLogEntity.class);
        verify(chain).append(checkpoint.capture());
        assertThat(checkpoint.getValue().getEventType()).isEqualTo(AuditEventType.AUDIT_ARCHIVED);
        assertThat(checkpoint.getValue().getContextData()).contains("private/audit-archive/4-5.jsonl");
        ArgumentCaptor<SecurityAuditArchiveEntity> archive = ArgumentCaptor.forClass(SecurityAuditArchiveEntity.class);
        verify(archives).saveAndFlush(archive.capture());
        assertThat(archive.getValue().getPreviousHash()).isEqualTo("hash-3");
        assertThat(archive.getValue().getLastHash()).isEqualTo("hash-5");
        verify(jdbc).update(startsWith("DELETE FROM t_security_audit_log"), eq(4L), eq(5L));
    }

    @Test
    void aRunAnotherReplicaArchivedIsSkippedAndRolledBack() {
        eligible(List.of(4L));
        rows(4, 4);
        when(jdbc.queryForObject(startsWith(ARCHIVE_COUNT), eq(Long.class), eq(4L), eq(4L))).thenReturn(1L);

        int deleted = service.applyRetention();

        assertThat(deleted).isZero();
        assertThat(status.isRollbackOnly()).isTrue();
        verify(archives, never()).saveAndFlush(any());
        verify(jdbc, never()).update(startsWith("DELETE"), anyLong(), anyLong());
    }

    @Test
    void aFailedUploadIsCountedAndLoggedAndDeletesNothing(CapturedOutput output) throws Exception {
        eligible(List.of(4L));
        rows(4, 4);
        doThrow(new RuntimeException("storage down")).when(storage).uploadFile(any(), anyString(), anyString());

        service.scheduledRun();

        assertThat(failures()).isEqualTo(1.0);
        assertThat(output).contains("Audit retention run failed");
        verify(tx, never()).execute(any());
    }

    @Test
    void aSuccessfulScheduledRunCountsNoFailure(CapturedOutput output) {
        eligible(List.of(4L));
        rows(4, 4);
        notArchivedYet();

        service.scheduledRun();

        assertThat(failures()).isZero();
        assertThat(output).contains("Retention archived and deleted 1 audit rows");
    }

    @Test
    void sequencesAreSplitIntoRunsWithNoGaps() {
        List<long[]> runs = AuditRetentionService.consecutiveRuns(List.of(1L, 2L, 3L, 7L, 9L, 10L));

        assertThat(runs).containsExactly(new long[]{1, 3}, new long[]{7, 7}, new long[]{9, 10});
    }

    private void eligible(List<Long> sequences) {
        when(jdbc.queryForList(anyString(), eq(Long.class), any(), any())).thenReturn(sequences);
    }

    private void rows(long first, long last) {
        List<SecurityAuditLogEntity> rows = new ArrayList<>();
        for (long sequence = first; sequence <= last; sequence++) {
            SecurityAuditLogEntity row = new SecurityAuditLogEntity();
            row.setSequenceNumber(sequence);
            row.setEventType(AuditEventType.ACCESS_DENIED);
            row.setTimestamp(DB_NOW.minusYears(6));
            row.setPreviousHash("hash-" + (sequence - 1));
            row.setCurrentHash("hash-" + sequence);
            rows.add(row);
        }
        when(logs.findBySequenceNumberBetweenOrderBySequenceNumberAsc(first, last)).thenReturn(rows);
    }

    private void notArchivedYet() {
        when(jdbc.queryForObject(startsWith(ARCHIVE_COUNT), eq(Long.class), any(), any())).thenReturn(0L);
    }

    private double failures() {
        Counter counter = meters.find("igrp.audit.write.failures").tag("path", "retention").counter();
        return counter == null ? 0.0 : counter.count();
    }
}
