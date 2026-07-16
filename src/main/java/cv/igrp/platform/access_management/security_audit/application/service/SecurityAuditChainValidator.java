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
 *
 * <p><strong>Legacy prefix.</strong> On a database upgraded through V10_1, every
 * row written before the chain existed carries the {@code ''} column default
 * rather than a hash — the migration deliberately does not backfill them, and
 * R2.6/N9 forbid rehashing in production. Those rows are unverifiable by
 * construction, not evidence of tampering, so the walk skips the contiguous
 * unhashed <em>prefix</em>, reports its size as {@code unverifiableLegacyRows},
 * and anchors on the first hashed row. Otherwise the validator would be
 * permanently red on every real deployment and a genuine tamper would be
 * indistinguishable from that baseline noise.
 *
 * <p>The prefix allowance is strictly positional: once the chain has started, a
 * blank hash is a break like any other, so hashes cannot be blanked to evade
 * detection.
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
     *         rows were checked, how many unhashed legacy rows were skipped, and
     *         (if broken) the sequence number of the first offending row.
     */
    @Transactional(readOnly = true)
    public Result validate() {
        List<SecurityAuditLogEntity> rows =
                repository.findAll(Sort.by(Sort.Direction.ASC, "sequenceNumber"));

        String expectedPrevious = SecurityAuditChainService.GENESIS_HASH;
        int checked = 0;
        int legacy = 0;
        boolean anchored = false;

        for (SecurityAuditLogEntity row : rows) {
            long seq = row.getSequenceNumber() != null ? row.getSequenceNumber() : -1L;

            // The GENESIS anchor (sequence 0) is the chain root, not a chained row.
            if (seq == 0L) {
                expectedPrevious = SecurityAuditChainService.isBlank(row.getCurrentHash())
                        ? SecurityAuditChainService.GENESIS_HASH : row.getCurrentHash();
                continue;
            }

            boolean hashed = !SecurityAuditChainService.isBlank(row.getCurrentHash());

            // Contiguous unhashed prefix: rows that predate the chain (V10_1 left
            // them at the '' default and prod never rehashes). Unverifiable, not tampered.
            if (!anchored && !hashed) {
                legacy++;
                continue;
            }

            // Past the prefix, a missing hash means a row was blanked out.
            if (!hashed) {
                log.error("[AUDIT][TAMPERING] Missing hash at sequence {} (row was blanked after the chain started).", seq);
                return Result.broken(seq, checked, legacy);
            }

            if (!anchored && legacy > 0) {
                // First hashed row after a legacy prefix. Its predecessor has no
                // hash, so linkage cannot be verified — check self-consistency only.
                String recomputed = chainService.computeHash(nz(row.getPreviousHash()), row);
                if (!recomputed.equals(row.getCurrentHash())) {
                    log.error("[AUDIT][TAMPERING] Hash mismatch at chain anchor sequence {} (row contents were altered).", seq);
                    return Result.broken(seq, checked, legacy);
                }
                log.info("[AUDIT] Chain anchored at sequence {} after {} unverifiable legacy rows.", seq, legacy);
            } else {
                if (!expectedPrevious.equals(nz(row.getPreviousHash()))) {
                    log.error("[AUDIT][TAMPERING] Chain linkage broken at sequence {} (previous_hash mismatch).", seq);
                    return Result.broken(seq, checked, legacy);
                }
                String recomputed = chainService.computeHash(expectedPrevious, row);
                if (!recomputed.equals(row.getCurrentHash())) {
                    log.error("[AUDIT][TAMPERING] Hash mismatch at sequence {} (row contents were altered).", seq);
                    return Result.broken(seq, checked, legacy);
                }
            }

            anchored = true;
            expectedPrevious = row.getCurrentHash();
            checked++;
        }

        log.info("[AUDIT] Chain integrity verified — {} rows validated, {} unverifiable legacy rows skipped.",
                checked, legacy);
        return Result.valid(checked, legacy);
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    /** Immutable validation outcome. */
    public record Result(boolean valid, int rowsChecked, Long brokenAt, int unverifiableLegacyRows) {
        static Result valid(int rowsChecked, int unverifiableLegacyRows) {
            return new Result(true, rowsChecked, null, unverifiableLegacyRows);
        }

        static Result broken(long brokenAt, int rowsChecked, int unverifiableLegacyRows) {
            return new Result(false, rowsChecked, brokenAt, unverifiableLegacyRows);
        }
    }
}
