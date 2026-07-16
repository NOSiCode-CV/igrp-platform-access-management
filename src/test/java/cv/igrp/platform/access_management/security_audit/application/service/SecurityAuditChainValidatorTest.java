package cv.igrp.platform.access_management.security_audit.application.service;

import cv.igrp.platform.access_management.security_audit.domain.entities.SecurityAuditLogEntity;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditCategory;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditEventType;
import cv.igrp.platform.access_management.security_audit.infrastructure.persistence.SecurityAuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityAuditChainValidatorTest {

    @Mock private SecurityAuditLogRepository repository;

    private SecurityAuditChainService chainService;
    private SecurityAuditChainValidator validator;

    @BeforeEach
    void setUp() {
        // computeHash does not touch the EntityManager, so a bare service is enough.
        chainService = new SecurityAuditChainService(repository, "validator-secret");
        validator = new SecurityAuditChainValidator(repository, chainService);
    }

    private SecurityAuditLogEntity genesis() {
        SecurityAuditLogEntity g = new SecurityAuditLogEntity();
        g.setSequenceNumber(0L);
        g.setEventType(AuditEventType.SYSTEM_CONFIGURATION_CHANGED);
        g.setCategory(AuditCategory.SYSTEM);
        g.setPreviousHash("");
        g.setCurrentHash(SecurityAuditChainService.GENESIS_HASH);
        g.setEpochMs(0L);
        return g;
    }

    /** Build a well-formed row chained onto {@code previousHash}. */
    private SecurityAuditLogEntity chainedRow(long seq, String previousHash, String user) {
        SecurityAuditLogEntity e = new SecurityAuditLogEntity();
        e.setSequenceNumber(seq);
        e.setEventType(AuditEventType.LOGIN_SUCCESS);
        e.setCategory(AuditCategory.AUTHENTICATION);
        e.setUserId(user);
        e.setIpAddress("203.0.113.1");
        e.setTimestamp(LocalDateTime.of(2026, 7, 15, 9, (int) seq, 0));
        e.setEpochMs(1_000L + seq);
        e.setPreviousHash(previousHash);
        e.setCurrentHash(chainService.computeHash(previousHash, e));
        return e;
    }

    @Test
    void intactChainValidates() {
        SecurityAuditLogEntity g = genesis();
        SecurityAuditLogEntity r1 = chainedRow(1, g.getCurrentHash(), "alice");
        SecurityAuditLogEntity r2 = chainedRow(2, r1.getCurrentHash(), "bob");
        when(repository.findAll(any(Sort.class))).thenReturn(List.of(g, r1, r2));

        SecurityAuditChainValidator.Result result = validator.validate();

        assertThat(result.valid()).isTrue();
        assertThat(result.rowsChecked()).isEqualTo(2);
        assertThat(result.brokenAt()).isNull();
    }

    @Test
    void mutatedRowContentIsDetected() {
        SecurityAuditLogEntity g = genesis();
        SecurityAuditLogEntity r1 = chainedRow(1, g.getCurrentHash(), "alice");
        SecurityAuditLogEntity r2 = chainedRow(2, r1.getCurrentHash(), "bob");
        // Tamper r2's content AFTER its hash was computed — current_hash no longer matches.
        r2.setUserId("mallory");
        when(repository.findAll(any(Sort.class))).thenReturn(List.of(g, r1, r2));

        SecurityAuditChainValidator.Result result = validator.validate();

        assertThat(result.valid()).isFalse();
        assertThat(result.brokenAt()).isEqualTo(2L);
    }

    @Test
    void brokenLinkageIsDetected() {
        SecurityAuditLogEntity g = genesis();
        SecurityAuditLogEntity r1 = chainedRow(1, g.getCurrentHash(), "alice");
        SecurityAuditLogEntity r2 = chainedRow(2, r1.getCurrentHash(), "bob");
        // Break the link: r2 no longer points at r1's current_hash.
        r2.setPreviousHash("not-the-previous-hash");
        when(repository.findAll(any(Sort.class))).thenReturn(List.of(g, r1, r2));

        SecurityAuditChainValidator.Result result = validator.validate();

        assertThat(result.valid()).isFalse();
        assertThat(result.brokenAt()).isEqualTo(2L);
    }

    @Test
    void emptyChainWithOnlyGenesisIsValid() {
        when(repository.findAll(any(Sort.class))).thenReturn(List.of(genesis()));

        SecurityAuditChainValidator.Result result = validator.validate();

        assertThat(result.valid()).isTrue();
        assertThat(result.rowsChecked()).isZero();
        assertThat(result.unverifiableLegacyRows()).isZero();
    }

    /** A row written before V10_1: sequence backfilled, hashes left at the '' default. */
    private SecurityAuditLogEntity legacyRow(long seq, String user) {
        SecurityAuditLogEntity e = new SecurityAuditLogEntity();
        e.setSequenceNumber(seq);
        e.setEventType(AuditEventType.LOGIN_SUCCESS);
        e.setCategory(AuditCategory.AUTHENTICATION);
        e.setUserId(user);
        e.setTimestamp(LocalDateTime.of(2026, 5, 14, 9, (int) seq, 0));
        e.setPreviousHash("");
        e.setCurrentHash("");
        return e;
    }

    /**
     * The upgraded-database shape from the demo env: an unhashed legacy prefix,
     * then the first chained row whose previous_hash is still the '' default.
     * The prefix is unverifiable by construction (prod never rehashes, R2.6), so
     * it must be reported — not called tampering.
     */
    @Test
    void unhashedLegacyPrefixIsSkippedAndTheChainAnchorsOnTheFirstHashedRow() {
        SecurityAuditLogEntity g = genesis();
        SecurityAuditLogEntity l1 = legacyRow(1, "old-1");
        SecurityAuditLogEntity l2 = legacyRow(2, "old-2");
        SecurityAuditLogEntity anchor = chainedRow(3, "", "alice");
        SecurityAuditLogEntity r4 = chainedRow(4, anchor.getCurrentHash(), "bob");
        when(repository.findAll(any(Sort.class))).thenReturn(List.of(g, l1, l2, anchor, r4));

        SecurityAuditChainValidator.Result result = validator.validate();

        assertThat(result.valid()).isTrue();
        assertThat(result.brokenAt()).isNull();
        assertThat(result.rowsChecked()).isEqualTo(2);
        assertThat(result.unverifiableLegacyRows()).isEqualTo(2);
    }

    /** The prefix allowance is positional — it must not become an evasion route. */
    @Test
    void blankedHashAfterTheChainStartedIsTampering() {
        SecurityAuditLogEntity g = genesis();
        SecurityAuditLogEntity r1 = chainedRow(1, g.getCurrentHash(), "alice");
        SecurityAuditLogEntity blanked = legacyRow(2, "mallory");
        when(repository.findAll(any(Sort.class))).thenReturn(List.of(g, r1, blanked));

        SecurityAuditChainValidator.Result result = validator.validate();

        assertThat(result.valid()).isFalse();
        assertThat(result.brokenAt()).isEqualTo(2L);
    }

    /** The anchor row itself is still content-verified, even though its linkage is not. */
    @Test
    void tamperedAnchorRowAfterALegacyPrefixIsDetected() {
        SecurityAuditLogEntity g = genesis();
        SecurityAuditLogEntity l1 = legacyRow(1, "old-1");
        SecurityAuditLogEntity anchor = chainedRow(2, "", "alice");
        anchor.setUserId("mallory");
        when(repository.findAll(any(Sort.class))).thenReturn(List.of(g, l1, anchor));

        SecurityAuditChainValidator.Result result = validator.validate();

        assertThat(result.valid()).isFalse();
        assertThat(result.brokenAt()).isEqualTo(2L);
        assertThat(result.unverifiableLegacyRows()).isEqualTo(1);
    }
}
