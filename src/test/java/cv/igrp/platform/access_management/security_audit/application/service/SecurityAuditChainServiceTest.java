package cv.igrp.platform.access_management.security_audit.application.service;

import cv.igrp.platform.access_management.security_audit.domain.entities.SecurityAuditLogEntity;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditCategory;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditEventType;
import cv.igrp.platform.access_management.security_audit.infrastructure.persistence.SecurityAuditLogRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityAuditChainServiceTest {

    private static final String SECRET = "unit-test-secret";

    @Mock private SecurityAuditLogRepository repository;
    @Mock private EntityManager entityManager;
    @Mock private Session session;

    private SecurityAuditChainService chainService;

    @BeforeEach
    void setUp() {
        chainService = new SecurityAuditChainService(repository, SECRET);
        ReflectionTestUtils.setField(chainService, "entityManager", entityManager);
    }

    private SecurityAuditLogEntity sampleRow() {
        SecurityAuditLogEntity e = new SecurityAuditLogEntity();
        e.setEventType(AuditEventType.LOGIN_SUCCESS);
        e.setCategory(AuditCategory.AUTHENTICATION);
        e.setUserId("user-1");
        e.setUsername("alice");
        e.setIpAddress("203.0.113.7");
        e.setTimestamp(LocalDateTime.of(2026, 7, 15, 10, 0, 0));
        return e;
    }

    @Test
    void appendOnEmptyChainAnchorsToGenesisAtSequenceOne() {
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(repository.findTopByOrderBySequenceNumberDesc()).thenReturn(Optional.empty());

        SecurityAuditLogEntity row = sampleRow();
        chainService.append(row);

        assertThat(row.getSequenceNumber()).isEqualTo(1L);
        assertThat(row.getPreviousHash()).isEqualTo(SecurityAuditChainService.GENESIS_HASH);
        assertThat(row.getEpochMs()).isNotNull();
        assertThat(row.getIpHash()).isNotBlank();
        // current_hash must recompute from the finalized fields (serialize excludes current_hash).
        assertThat(row.getCurrentHash())
                .isEqualTo(chainService.computeHash(row.getPreviousHash(), row));
        verify(repository).save(row);
    }

    /**
     * On a database upgraded through V10_1 the tip is a legacy row whose
     * current_hash is the '' column default, not null. Blank must be treated as
     * "no hash" so the first chained row anchors on GENESIS rather than
     * inheriting an empty previous_hash.
     */
    @Test
    void appendOnABlankHashedLegacyTipAnchorsToGenesis() {
        SecurityAuditLogEntity legacyTip = sampleRow();
        legacyTip.setSequenceNumber(4717L);
        legacyTip.setPreviousHash("");
        legacyTip.setCurrentHash("");
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(repository.findTopByOrderBySequenceNumberDesc()).thenReturn(Optional.of(legacyTip));

        SecurityAuditLogEntity row = sampleRow();
        chainService.append(row);

        assertThat(row.getSequenceNumber()).isEqualTo(4718L);
        assertThat(row.getPreviousHash()).isEqualTo(SecurityAuditChainService.GENESIS_HASH);
        assertThat(row.getCurrentHash())
                .isEqualTo(chainService.computeHash(SecurityAuditChainService.GENESIS_HASH, row));
    }

    @Test
    void appendLinksToTheExistingChainTip() {
        SecurityAuditLogEntity tip = sampleRow();
        tip.setSequenceNumber(5L);
        tip.setCurrentHash("deadbeef");
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(repository.findTopByOrderBySequenceNumberDesc()).thenReturn(Optional.of(tip));

        SecurityAuditLogEntity row = sampleRow();
        chainService.append(row);

        assertThat(row.getSequenceNumber()).isEqualTo(6L);
        assertThat(row.getPreviousHash()).isEqualTo("deadbeef");
        verify(repository).save(row);
    }

    @Test
    void computeHashIsDeterministicAndSensitiveToPreviousHash() {
        SecurityAuditLogEntity row = sampleRow();
        row.setSequenceNumber(1L);
        row.setEpochMs(1_000L);

        String h1 = chainService.computeHash("PREV", row);
        String h2 = chainService.computeHash("PREV", row);
        String h3 = chainService.computeHash("OTHER", row);

        assertThat(h1).isEqualTo(h2);
        assertThat(h1).isNotEqualTo(h3);
        assertThat(h1).hasSize(64); // HMAC-SHA256 hex
    }

    @Test
    void editingAnyContentFieldChangesTheHash() {
        SecurityAuditLogEntity row = sampleRow();
        row.setSequenceNumber(1L);
        row.setEpochMs(1_000L);
        String before = chainService.computeHash("PREV", row);

        row.setUserId("tampered");
        String after = chainService.computeHash("PREV", row);

        assertThat(after).isNotEqualTo(before);
    }

    @Test
    void hashIpReturnsNullForBlankAndStableHexOtherwise() {
        assertThat(chainService.hashIp(null)).isNull();
        assertThat(chainService.hashIp("  ")).isNull();
        String a = chainService.hashIp("203.0.113.7");
        String b = chainService.hashIp("203.0.113.7");
        assertThat(a).isEqualTo(b).hasSize(64);
        assertThat(chainService.hashIp("203.0.113.8")).isNotEqualTo(a);
    }

    @Test
    void appendAcquiresTheChainLockBeforeReadingTheTip() {
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(repository.findTopByOrderBySequenceNumberDesc()).thenReturn(Optional.empty());

        chainService.append(sampleRow());

        // The advisory lock is taken on the JPA session's own connection.
        verify(session).doWork(any());
    }
}
