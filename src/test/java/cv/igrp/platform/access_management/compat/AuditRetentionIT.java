package cv.igrp.platform.access_management.compat;

import cv.igrp.platform.access_management.security_audit.application.service.AuditRetentionService;
import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditChainService;
import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditChainValidator;
import cv.igrp.platform.access_management.security_audit.domain.entities.SecurityAuditLogEntity;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditCategory;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditEventType;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditStatus;
import cv.igrp.platform.filemanager.StorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * v3-23 (#30) — retention archives audit rows to storage, then deletes them, and
 * only rows past the configured period (default 1825 days). The hash chain stays
 * verifiable across the gap.
 */
class AuditRetentionIT extends AbstractCompatIT {

    @Autowired
    AuditRetentionService retention;
    @Autowired
    SecurityAuditChainService chain;
    @Autowired
    SecurityAuditChainValidator validator;
    @Autowired
    StorageService storage;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    TransactionTemplate tx;

    @Test
    @DisplayName("Rows past the period are archived to storage, then deleted; recent rows stay; the chain still validates")
    void archivesThenDeletesOnlyRowsPastThePeriod() throws Exception {
        String old = marker();
        String insidePeriod = marker();
        String recent = marker();
        LocalDateTime sixYearsAgo = LocalDateTime.now().minusYears(6);
        for (int i = 0; i < 3; i++) {
            append(old, sixYearsAgo.plusMinutes(i));
        }
        // Past the 365-day floor the database enforces, but inside the configured
        // 1825 days: only the configured period decides that it stays.
        append(insidePeriod, LocalDateTime.now().minusYears(2));
        append(recent, LocalDateTime.now());
        List<Long> oldSequences = jdbc.queryForList(
                "SELECT sequence_number FROM t_security_audit_log WHERE decision_reason = ? ORDER BY 1",
                Long.class, old);

        retention.applyRetention();

        assertThat(countByReason(old)).isZero();
        assertThat(countByReason(insidePeriod)).isOne();
        assertThat(countByReason(recent)).isOne();

        Map<String, Object> archive = jdbc.queryForMap(
                "SELECT storage_key, row_count FROM t_security_audit_archive "
                        + "WHERE first_sequence <= ? AND last_sequence >= ?",
                oldSequences.getFirst(), oldSequences.getLast());
        String key = (String) archive.get("storage_key");
        assertThat(key).startsWith("private/");
        try (InputStream file = storage.downloadFile(key)) {
            String content = new String(file.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(content.split(old, -1)).hasSize(4);
        }

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM t_security_audit_log WHERE event_type = 'AUDIT_ARCHIVED' AND context_data LIKE ?",
                Long.class, "%" + key + "%")).isOne();
        assertThat(validator.validate().valid()).isTrue();
    }

    @Test
    @DisplayName("An old row that has not been archived cannot be deleted")
    void oldRowThatIsNotArchivedCannotBeDeleted() {
        String old = marker();
        append(old, LocalDateTime.now().minusYears(6));

        assertThatThrownBy(() -> jdbc.update("DELETE FROM t_security_audit_log WHERE decision_reason = ?", old))
                .isInstanceOf(DataAccessException.class);
        assertThat(countByReason(old)).isOne();
    }

    private void append(String marker, LocalDateTime timestamp) {
        tx.executeWithoutResult(status -> {
            SecurityAuditLogEntity row = new SecurityAuditLogEntity();
            row.setEventType(AuditEventType.ACCESS_DENIED);
            row.setCategory(AuditCategory.AUTHORIZATION);
            row.setStatus(AuditStatus.ACCESS_DENIED);
            row.setDecisionReason(marker);
            row.setTimestamp(timestamp);
            chain.append(row);
        });
    }

    private long countByReason(String marker) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM t_security_audit_log WHERE decision_reason = ?", Long.class, marker);
    }

    private static String marker() {
        return "it-" + UUID.randomUUID();
    }
}
