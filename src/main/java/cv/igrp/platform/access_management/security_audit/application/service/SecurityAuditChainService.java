package cv.igrp.platform.access_management.security_audit.application.service;

import cv.igrp.platform.access_management.security_audit.domain.entities.SecurityAuditLogEntity;
import cv.igrp.platform.access_management.security_audit.infrastructure.persistence.SecurityAuditLogRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

/**
 * Tamper-evident hash chain over {@link SecurityAuditLogEntity}.
 *
 * <p>Each appended row carries {@code previous_hash} (the chain tip's
 * {@code current_hash}) and {@code current_hash =
 * HMAC-SHA256(secret, previous_hash || serialized-fields)}. The write path is
 * serialized cluster-wide by a Postgres advisory lock taken on the JPA
 * connection <em>inside the caller's transaction</em>, so the read-tip →
 * compute → insert sequence cannot fork under concurrent writers (R2.1–R2.3).
 *
 * <p>{@code sequence_number} is assigned here (not by a DB identity) so it stays
 * contiguous even when an insert rolls back, and so it participates in the hash.
 */
@Service
public class SecurityAuditChainService {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditChainService.class);

    /** current_hash of the seeded sequence-0 anchor (see V10_1 migration). */
    public static final String GENESIS_HASH = "GENESIS";

    /** Arbitrary but stable 64-bit key for pg_advisory_xact_lock ("AUDITCHA"). */
    private static final long CHAIN_LOCK_KEY = 0x4155444954434841L;

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final String FIELD_SEP = "|";
    private static final String DEV_FALLBACK_SECRET = "dev-secret-change-in-prod";

    @PersistenceContext
    private EntityManager entityManager;

    private final SecurityAuditLogRepository repository;
    private final String chainSecret;

    public SecurityAuditChainService(SecurityAuditLogRepository repository,
                                     @Value("${igrp.audit.chain.secret:}") String chainSecret) {
        this.repository = repository;
        if (chainSecret == null || chainSecret.isBlank()) {
            log.warn("[AUDIT] igrp.audit.chain.secret is not set; falling back to a development secret. "
                    + "This MUST be configured in production (AUDIT_CHAIN_SECRET).");
            this.chainSecret = DEV_FALLBACK_SECRET;
        } else {
            this.chainSecret = chainSecret;
        }
    }

    /**
     * Finalize and persist an audit row as the next link in the chain. Must run
     * inside the caller's transaction (it takes a transaction-scoped advisory
     * lock that is released on commit/rollback).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(SecurityAuditLogEntity entity) {
        // Write the transaction's other pending changes first, so no row lock is
        // waited for while the chain lock is held. A writer holding the chain lock
        // and waiting on a row lock can otherwise wait on a request that is itself
        // waiting for the chain lock in a REQUIRES_NEW access event.
        flushPendingChanges();
        acquireChainLock();

        // Must happen before the hash is computed — see normalizeTemporalPrecision.
        normalizeTemporalPrecision(entity);

        SecurityAuditLogEntity tip = repository.findTopByOrderBySequenceNumberDesc().orElse(null);
        long nextSequence = tip != null && tip.getSequenceNumber() != null ? tip.getSequenceNumber() + 1 : 1L;
        // A legacy tip (rows predating V10_1) has current_hash = '' — the column
        // default — not null. Treat blank as "no hash" so the first chained row
        // anchors on GENESIS instead of inheriting an empty previous_hash.
        String tipHash = tip != null ? tip.getCurrentHash() : null;
        String previousHash = isBlank(tipHash) ? GENESIS_HASH : tipHash;

        entity.setSequenceNumber(nextSequence);
        entity.setPreviousHash(previousHash);
        if (entity.getEpochMs() == null) {
            entity.setEpochMs(System.currentTimeMillis());
        }
        entity.setIpHash(hashIp(entity.getIpAddress()));
        entity.setCurrentHash(computeHash(previousHash, entity));

        // Flushed here, not at commit, so a failed insert surfaces inside the
        // caller's try block, where it is logged and counted.
        repository.saveAndFlush(entity);
    }

    /** Flushes the current transaction's pending changes to the database. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void flushPendingChanges() {
        entityManager.flush();
    }

    /**
     * Round every hashed temporal field down to the precision the database can
     * actually store, <em>before</em> the row is hashed.
     *
     * <p>Postgres {@code timestamp}/{@code timestamptz} hold microseconds (max
     * precision 6), while {@code LocalDateTime.now()} and {@code Instant.now()}
     * carry nanoseconds. Hashing the nanosecond value and then recomputing over
     * the truncated value read back from the database yields a different HMAC, so
     * an untouched row reports as tampered. Because {@code timestamp} is always
     * populated, that made tamper detection a permanent false positive on every
     * install — and an intermittent one, since a row whose nanos happen to land on
     * an exact microsecond survives.
     *
     * <p>Truncating here keeps the in-memory row and the stored row genuinely
     * identical, so {@code computeHash} is stable across the round trip. Note this
     * is a precision problem, not a type problem: moving these fields to
     * {@code Instant} would not help, because Postgres cannot store nanoseconds
     * either.
     *
     * <p>{@code append} is the single write path into the append-only table, so
     * normalizing here covers every caller and every future temporal column.
     */
    private static void normalizeTemporalPrecision(SecurityAuditLogEntity entity) {
        if (entity.getTimestamp() != null) {
            entity.setTimestamp(entity.getTimestamp().truncatedTo(ChronoUnit.MICROS));
        }
        if (entity.getPeriodStart() != null) {
            entity.setPeriodStart(entity.getPeriodStart().truncatedTo(ChronoUnit.MICROS));
        }
        if (entity.getPeriodEnd() != null) {
            entity.setPeriodEnd(entity.getPeriodEnd().truncatedTo(ChronoUnit.MICROS));
        }
    }

    /**
     * HMAC-SHA256(secret, previousHash || serialized(entity)). Public so the
     * validator recomputes with the exact same formula.
     */
    public String computeHash(String previousHash, SecurityAuditLogEntity entity) {
        String input = nullSafe(previousHash) + FIELD_SEP + serialize(entity);
        return hmac(input);
    }

    /** HMAC-SHA256(secret, rawIp); null/blank IP → null. */
    public String hashIp(String rawIp) {
        if (rawIp == null || rawIp.isBlank()) {
            return null;
        }
        return hmac(rawIp);
    }

    /**
     * Deterministic field serialization. The order and set of fields must match
     * exactly between {@link #append} and the validator. {@code id} is excluded
     * because it is assigned at persist time (not available when hashing);
     * {@code sequence_number} anchors row identity instead.
     */
    private String serialize(SecurityAuditLogEntity e) {
        return String.join(FIELD_SEP,
                nullSafe(e.getSequenceNumber()),
                nullSafe(e.getEventType()),
                nullSafe(e.getCategory()),
                nullSafe(e.getUserId()),
                nullSafe(e.getUsername()),
                nullSafe(e.getSessionId()),
                nullSafe(e.getIpAddress()),
                nullSafe(e.getIpHash()),
                nullSafe(e.getUserAgent()),
                nullSafe(e.getCorrelationId()),
                nullSafe(e.getRequestPath()),
                nullSafe(e.getDecisionReason()),
                nullSafe(e.getContextData()),
                nullSafe(e.getEpochMs()),
                nullSafe(e.getTimestamp()),
                // Phase 2 report columns (all persisted fields participate — R2.2).
                // `device` is intentionally excluded: it is never persisted, only
                // derived at read time from user_agent.
                nullSafe(e.getApplicationModule()),
                nullSafe(e.getAccessRole()),
                nullSafe(e.getOperationState()),
                nullSafe(e.getAuthorizedBy()),
                nullSafe(e.getStatus()),
                nullSafe(e.getAction()),
                nullSafe(e.getSettingsArea()),
                nullSafe(e.getSettingsEntityType()),
                nullSafe(e.getSettingsOperation()),
                nullSafe(e.getEntityName()),
                nullSafe(e.getRelatedEntity()),
                nullSafe(e.getPreviousValue()),
                nullSafe(e.getNewValue()),
                nullSafe(e.getPeriodStart()),
                nullSafe(e.getPeriodEnd()));
    }

    private String hmac(String input) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(chainSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] digest = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            // Never happens for HmacSHA256 with a non-null key; surface loudly if it does.
            throw new IllegalStateException("[AUDIT] Unable to compute HMAC-SHA256 for the audit chain", ex);
        }
    }

    private void acquireChainLock() {
        entityManager.unwrap(Session.class).doWork(connection -> {
            try (var ps = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                ps.setLong(1, CHAIN_LOCK_KEY);
                ps.execute();
            }
        });
    }

    private static String nullSafe(Object value) {
        return value == null ? "" : value.toString();
    }

    /** A hash column is "absent" when it is null or the '' column default. */
    static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
