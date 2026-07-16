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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Guards the hash chain against fields that do not survive a database round trip.
 *
 * <p>The chain hashes a row in memory and the validator recomputes that hash from
 * the row read back out of Postgres. Any hashed field the database cannot store
 * verbatim therefore reports as tampered. {@code timestamp} did exactly that:
 * {@code LocalDateTime.now()} carries nanoseconds, Postgres {@code timestamp}
 * keeps only microseconds, so tamper detection was a permanent — and
 * intermittent — false positive on every install, fresh ones included.
 *
 * <p>The existing chain tests could not catch it for two independent reasons:
 * they build rows with whole-second timestamps (zero nanos), and they never
 * persist. This test closes both gaps by driving real {@code append} calls and
 * simulating what {@code timestamp(6)} does to a row on the way in.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SecurityAuditChainRoundTripTest {

    private static final String SECRET = "round-trip-secret";

    @Mock private SecurityAuditLogRepository repository;
    @Mock private EntityManager entityManager;
    @Mock private Session session;

    private SecurityAuditChainService chainService;
    private SecurityAuditChainValidator validator;

    /** Stands in for t_security_audit_log, holding rows at storable precision. */
    private final List<SecurityAuditLogEntity> db = new ArrayList<>();
    /** Timestamps exactly as append submitted them, captured before the DB truncates. */
    private final List<LocalDateTime> submittedTimestamps = new ArrayList<>();
    private SecurityAuditLogEntity genesis;

    @BeforeEach
    void setUp() {
        chainService = new SecurityAuditChainService(repository, SECRET);
        ReflectionTestUtils.setField(chainService, "entityManager", entityManager);
        validator = new SecurityAuditChainValidator(repository, chainService);

        genesis = new SecurityAuditLogEntity();
        genesis.setSequenceNumber(0L);
        genesis.setEventType(AuditEventType.SYSTEM_CONFIGURATION_CHANGED);
        genesis.setCategory(AuditCategory.SYSTEM);
        genesis.setPreviousHash("");
        genesis.setCurrentHash(SecurityAuditChainService.GENESIS_HASH);
        genesis.setEpochMs(0L);

        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(repository.save(any())).thenAnswer(invocation -> {
            SecurityAuditLogEntity row = invocation.getArgument(0);
            submittedTimestamps.add(row.getTimestamp());
            storeLikePostgres(row);
            db.add(row);
            return row;
        });
        when(repository.findTopByOrderBySequenceNumberDesc())
                .thenAnswer(invocation -> Optional.of(db.isEmpty() ? genesis : db.get(db.size() - 1)));
        when(repository.findAll(any(Sort.class))).thenAnswer(invocation -> {
            List<SecurityAuditLogEntity> all = new ArrayList<>();
            all.add(genesis);
            all.addAll(db);
            return all;
        });
    }

    /**
     * What Postgres does to a row on INSERT: {@code timestamp}/{@code timestamptz}
     * have microsecond resolution, so any nanosecond remainder is dropped.
     */
    private static void storeLikePostgres(SecurityAuditLogEntity row) {
        if (row.getTimestamp() != null) {
            row.setTimestamp(row.getTimestamp().truncatedTo(ChronoUnit.MICROS));
        }
        if (row.getPeriodStart() != null) {
            row.setPeriodStart(row.getPeriodStart().truncatedTo(ChronoUnit.MICROS));
        }
        if (row.getPeriodEnd() != null) {
            row.setPeriodEnd(row.getPeriodEnd().truncatedTo(ChronoUnit.MICROS));
        }
    }

    private static SecurityAuditLogEntity row(String user, LocalDateTime timestamp) {
        SecurityAuditLogEntity e = new SecurityAuditLogEntity();
        e.setEventType(AuditEventType.LOGIN_SUCCESS);
        e.setCategory(AuditCategory.AUTHENTICATION);
        e.setUserId(user);
        e.setUsername(user);
        e.setIpAddress("203.0.113.7");
        e.setTimestamp(timestamp);
        return e;
    }

    @Test
    void chainWrittenWithNanosecondTimestampsStillValidatesAfterTheRoundTrip() {
        // Nanos deliberately not whole microseconds — the case that always failed.
        chainService.append(row("alice", LocalDateTime.of(2026, 7, 16, 9, 47, 21).withNano(827_108_300)));
        chainService.append(row("bob", LocalDateTime.of(2026, 7, 16, 9, 47, 22).withNano(133_105_777)));
        chainService.append(row("carol", LocalDateTime.of(2026, 7, 16, 9, 47, 23).withNano(1)));

        SecurityAuditChainValidator.Result result = validator.validate();

        assertThat(result.valid()).isTrue();
        assertThat(result.brokenAt()).isNull();
        assertThat(result.rowsChecked()).isEqualTo(3);
    }

    @Test
    void appendSubmitsTimestampsAtAPrecisionPostgresCanHold() {
        chainService.append(row("alice", LocalDateTime.of(2026, 7, 16, 9, 47, 21).withNano(827_108_300)));

        // Asserted on the value append handed to save(), not on the stored row —
        // the stored row is truncated by the simulator either way, so checking it
        // would pass with or without the fix.
        assertThat(submittedTimestamps.get(0).getNano() % 1000)
                .as("append must hash a value the database can store verbatim")
                .isZero();
    }

    /** periodStart/periodEnd are Instants and carry the identical exposure. */
    @Test
    void periodBoundsAlsoSurviveTheRoundTrip() {
        SecurityAuditLogEntity e = row("dave", LocalDateTime.of(2026, 7, 16, 9, 47, 24).withNano(4_711));
        e.setPeriodStart(Instant.parse("2025-06-01T08:14:00.827108300Z"));
        e.setPeriodEnd(Instant.parse("2025-06-08T09:12:00.133105777Z"));

        chainService.append(e);

        SecurityAuditChainValidator.Result result = validator.validate();

        assertThat(result.valid()).isTrue();
        assertThat(db.get(0).getPeriodStart().getNano() % 1000).isZero();
        assertThat(db.get(0).getPeriodEnd().getNano() % 1000).isZero();
    }

    /** Normalizing must not blunt the alarm: real tampering still has to be caught. */
    @Test
    void tamperingIsStillDetectedAfterTheRoundTrip() {
        chainService.append(row("alice", LocalDateTime.of(2026, 7, 16, 9, 47, 21).withNano(827_108_300)));
        chainService.append(row("bob", LocalDateTime.of(2026, 7, 16, 9, 47, 22).withNano(133_105_777)));

        db.get(1).setUserId("mallory");

        SecurityAuditChainValidator.Result result = validator.validate();

        assertThat(result.valid()).isFalse();
        assertThat(result.brokenAt()).isEqualTo(2L);
    }
}
