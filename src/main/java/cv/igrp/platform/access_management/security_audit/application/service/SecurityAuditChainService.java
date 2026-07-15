package cv.igrp.platform.access_management.security_audit.application.service;

import cv.igrp.platform.access_management.security_audit.domain.entities.SecurityAuditLogEntity;
import cv.igrp.platform.access_management.security_audit.infrastructure.persistence.SecurityAuditLogRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

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
        acquireChainLock();

        SecurityAuditLogEntity tip = repository.findTopByOrderBySequenceNumberDesc().orElse(null);
        long nextSequence = tip != null && tip.getSequenceNumber() != null ? tip.getSequenceNumber() + 1 : 1L;
        String previousHash = tip != null && tip.getCurrentHash() != null ? tip.getCurrentHash() : GENESIS_HASH;

        entity.setSequenceNumber(nextSequence);
        entity.setPreviousHash(previousHash);
        if (entity.getEpochMs() == null) {
            entity.setEpochMs(System.currentTimeMillis());
        }
        entity.setIpHash(hashIp(entity.getIpAddress()));
        entity.setCurrentHash(computeHash(previousHash, entity));

        repository.save(entity);
    }

    /**
     * Recompute {@code previous_hash}/{@code current_hash} for every row from the
     * GENESIS anchor forward. Used only after a schema change that widens the
     * hash input (guarded by {@code igrp.audit.chain.rehash-on-boot}, dev/staging
     * only). Requires the append-only trigger to be bypassed via the
     * {@code app.audit_purge} GUC, which is set here for the transaction.
     */
    @Transactional
    public int rehashAll() {
        setAuditPurgeGuc();

        List<SecurityAuditLogEntity> rows = repository.findAll(Sort.by(Sort.Direction.ASC, "sequenceNumber"));
        String previousHash = GENESIS_HASH;
        int rewritten = 0;
        for (SecurityAuditLogEntity row : rows) {
            if (row.getSequenceNumber() != null && row.getSequenceNumber() == 0L) {
                // GENESIS anchor — keep its fixed hash as the chain root.
                previousHash = row.getCurrentHash() != null ? row.getCurrentHash() : GENESIS_HASH;
                continue;
            }
            row.setPreviousHash(previousHash);
            String recomputed = computeHash(previousHash, row);
            row.setCurrentHash(recomputed);
            previousHash = recomputed;
            rewritten++;
        }
        repository.saveAll(rows);
        log.info("[AUDIT] rehashAll rewrote {} audit rows.", rewritten);
        return rewritten;
    }

    /**
     * Purge all audit rows except the GENESIS anchor and let the chain restart
     * from it. Gated at the endpoint by {@code igrp.audit.purge}. Bypasses the
     * append-only trigger via the {@code app.audit_purge} GUC.
     *
     * @return number of rows removed.
     */
    @Transactional
    public long purge() {
        setAuditPurgeGuc();
        Number before = (Number) entityManager
                .createNativeQuery("SELECT count(*) FROM t_security_audit_log WHERE sequence_number <> 0")
                .getSingleResult();
        entityManager
                .createNativeQuery("DELETE FROM t_security_audit_log WHERE sequence_number <> 0")
                .executeUpdate();
        long removed = before.longValue();
        log.info("[AUDIT] purge removed {} audit rows (GENESIS anchor retained).", removed);
        return removed;
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
                nullSafe(e.getTimestamp()));
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

    private void setAuditPurgeGuc() {
        entityManager.unwrap(Session.class).doWork(connection -> {
            try (var st = connection.createStatement()) {
                st.execute("SET LOCAL app.audit_purge = 'true'");
            }
        });
    }

    private static String nullSafe(Object value) {
        return value == null ? "" : value.toString();
    }
}
