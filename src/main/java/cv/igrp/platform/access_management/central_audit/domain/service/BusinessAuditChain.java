package cv.igrp.platform.access_management.central_audit.domain.service;

import cv.igrp.platform.access_management.central_audit.application.dto.BusinessAuditEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * The hash of a business audit event in its service's chain (v3-22, #32):
 * {@code HMAC-SHA256(secret, previous_hash || fields)}, with the secret of AM's
 * own chain ({@code igrp.audit.chain.secret}). Every stored field but the hashes
 * takes part, in a fixed order, so the validator recomputes it from the row alone.
 */
@Component
public class BusinessAuditChain {

    private static final Logger LOGGER = LoggerFactory.getLogger(BusinessAuditChain.class);

    /** The previous hash of a service's first event. */
    public static final String GENESIS = "GENESIS";

    private static final String ALGORITHM = "HmacSHA256";
    private static final String DEV_FALLBACK_SECRET = "dev-secret-change-in-prod";

    private final String secret;

    public BusinessAuditChain(@Value("${igrp.audit.chain.secret:}") String secret) {
        if (secret == null || secret.isBlank()) {
            LOGGER.warn("[AUDIT] igrp.audit.chain.secret is not set; the business audit chain uses a development secret.");
            this.secret = DEV_FALLBACK_SECRET;
        } else {
            this.secret = secret;
        }
    }

    /**
     * The event's hash, after {@code previousHash}, at {@code sequenceNumber} of its
     * service's chain, stored at {@code receivedAt}. Each field is written as its
     * length and its text, and a missing one as {@code -1}, so no two rows give the
     * same input.
     */
    public String hash(String previousHash, long sequenceNumber, BusinessAuditEventDTO event, Instant receivedAt) {
        StringBuilder input = new StringBuilder();
        for (String field : new String[]{previousHash, Long.toString(sequenceNumber), event.id().toString(),
                event.service(), event.event(), event.resource(), event.payload(), event.username(), event.agent(),
                event.result(), event.traceId(), event.occurredAt().toString(), receivedAt.toString()}) {
            if (field == null) {
                input.append("-1:");
            } else {
                input.append(field.length()).append(':').append(field);
            }
        }
        // Preserve historical hashes when act is absent; protect the new field when present.
        if (event.act() != null) input.append("act:").append(event.act().length()).append(':').append(event.act());
        return hmac(input.toString());
    }

    private String hmac(String input) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("[AUDIT] Unable to compute HMAC-SHA256 for the business audit chain", ex);
        }
    }
}
