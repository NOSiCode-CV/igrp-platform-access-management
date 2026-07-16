package cv.igrp.platform.access_management.security_audit.application.service;

import cv.igrp.platform.access_management.security_audit.domain.entities.SecurityAuditLogEntity;
import cv.igrp.platform.access_management.security_audit.infrastructure.persistence.SecurityAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Walks the {@code t_security_audit_log} table in sequence order and verifies
 * the hash chain: each row's {@code previous_hash} must equal the prior row's
 * {@code current_hash}, and its {@code current_hash} must recompute from the
 * stored fields. Reports the first break (R2.5).
 */
@Service
public class SecurityAuditChainValidator {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditChainValidator.class);

    private final SecurityAuditLogRepository repository;
    private final SecurityAuditChainService chainService;

    public SecurityAuditChainValidator(SecurityAuditLogRepository repository,
                                       SecurityAuditChainService chainService) {
        this.repository = repository;
        this.chainService = chainService;
    }

    /**
     * @return a {@link Result} describing whether the chain is intact, how many
     *         rows were checked, and (if broken) the sequence number of the
     *         first offending row.
     */
    @Transactional(readOnly = true)
    public Result validate() {
        List<SecurityAuditLogEntity> rows =
                repository.findAll(Sort.by(Sort.Direction.ASC, "sequenceNumber"));

        String expectedPrevious = SecurityAuditChainService.GENESIS_HASH;
        int checked = 0;

        for (SecurityAuditLogEntity row : rows) {
            long seq = row.getSequenceNumber() != null ? row.getSequenceNumber() : -1L;

            // The GENESIS anchor (sequence 0) is the chain root, not a chained row.
            if (seq == 0L) {
                expectedPrevious = row.getCurrentHash() != null
                        ? row.getCurrentHash() : SecurityAuditChainService.GENESIS_HASH;
                continue;
            }

            if (!expectedPrevious.equals(row.getPreviousHash())) {
                log.error("[AUDIT][TAMPERING] Chain linkage broken at sequence {} (previous_hash mismatch).", seq);
                return Result.broken(seq, checked);
            }

            String recomputed = chainService.computeHash(expectedPrevious, row);
            if (!recomputed.equals(row.getCurrentHash())) {
                log.error("[AUDIT][TAMPERING] Hash mismatch at sequence {} (row contents were altered).", seq);
                return Result.broken(seq, checked);
            }

            expectedPrevious = row.getCurrentHash();
            checked++;
        }

        log.info("[AUDIT] Chain integrity verified — {} rows validated.", checked);
        return Result.valid(checked);
    }

    /** Immutable validation outcome. */
    public record Result(boolean valid, int rowsChecked, Long brokenAt) {
        static Result valid(int rowsChecked) {
            return new Result(true, rowsChecked, null);
        }

        static Result broken(long brokenAt, int rowsChecked) {
            return new Result(false, rowsChecked, brokenAt);
        }
    }
}
