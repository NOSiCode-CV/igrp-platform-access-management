package cv.igrp.platform.access_management.central_audit.domain.service;

import cv.igrp.platform.access_management.central_audit.application.dto.BusinessAuditEventDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The central store of business audit events (v3-22, #32; spec "Audit"). Each
 * event joins its source service's hash chain. A service's chain is extended by
 * one writer at a time (a transaction-scoped advisory lock per service), and an
 * event already stored is not stored again: Kafka delivers at least once.
 */
@Service
public class BusinessAuditStore {

    /** First half of the two-int advisory lock key, distinct from the other AM locks. */
    private static final int LOCK_CLASS = 0x42415544; // "BAUD"

    // The event's JSON as the starter writes it (Audit.Event): these fields, in camelCase.
    private static final String ID = "id";
    private static final String EVENT = "event";
    private static final String RESOURCE = "resource";
    private static final String PAYLOAD = "payload";
    private static final String USERNAME = "username";
    private static final String SERVICE = "service";
    private static final String AGENT = "agent";
    private static final String RESULT = "result";
    private static final String TRACE_ID = "traceId";
    private static final String OCCURRED_AT = "occurredAt";

    private static final List<String> REQUIRED = List.of(ID, EVENT, RESOURCE, USERNAME, SERVICE, RESULT, TRACE_ID,
            OCCURRED_AT);

    /** The longest text each column takes (V28_1): a longer one could never be stored. */
    private static final Map<String, Integer> MAX_LENGTH = Map.of(EVENT, 128, RESOURCE, 512, USERNAME, 255,
            SERVICE, 128, AGENT, 255, RESULT, 32, TRACE_ID, 64);

    private static final String TIP = """
            SELECT sequence_number, current_hash FROM t_business_audit_event
             WHERE service = ? ORDER BY sequence_number DESC LIMIT 1""";

    private static final String INSERT = """
            INSERT INTO t_business_audit_event (id, service, sequence_number, event, resource, payload, username,
                                                agent, result, trace_id, occurred_at, received_at, previous_hash,
                                                current_hash)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

    /** A message that is not an audit event: it can never be stored, so it is not retried. */
    public static final class InvalidEventException extends RuntimeException {
        public InvalidEventException(String message) {
            super(message);
        }
    }

    /** Keeps a payload's numbers exact: a decimal is not rounded through a double. */
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
            .build();

    private final JdbcTemplate jdbc;
    private final BusinessAuditChain chain;

    public BusinessAuditStore(JdbcTemplate jdbc, BusinessAuditChain chain) {
        this.jdbc = jdbc;
        this.chain = chain;
    }

    /**
     * Parses an event published on {@code igrp.audit} with Kafka key {@code key}:
     * the relay keys each event by its service, and an event that names another
     * service is refused.
     */
    public static BusinessAuditEventDTO parse(String key, String message) {
        BusinessAuditEventDTO event = parse(message);
        if (!event.service().equals(key)) {
            throw new InvalidEventException("The audit event's service is not its key " + key);
        }
        return event;
    }

    /** Parses an event published on {@code igrp.audit}. */
    public static BusinessAuditEventDTO parse(String message) {
        JsonNode node = readTree(message);
        for (String field : REQUIRED) {
            if (!node.hasNonNull(field) || node.get(field).asString().isBlank()) {
                throw new InvalidEventException("The audit event has no " + field);
            }
        }
        MAX_LENGTH.forEach((field, max) -> checkText(field, text(node, field), max));
        try {
            JsonNode payload = node.get(PAYLOAD);
            return new BusinessAuditEventDTO(UUID.fromString(text(node, ID)), text(node, EVENT), text(node, RESOURCE),
                    payload == null || payload.isNull() ? null : JSON.writeValueAsString(payload),
                    text(node, USERNAME), text(node, SERVICE), text(node, AGENT), text(node, RESULT),
                    text(node, TRACE_ID),
                    // Postgres keeps microseconds: the hash must be of what is stored.
                    Instant.parse(text(node, OCCURRED_AT)).truncatedTo(ChronoUnit.MICROS));
        } catch (IllegalArgumentException | java.time.format.DateTimeParseException ex) {
            throw new InvalidEventException("The audit event has an invalid id or time: " + ex.getMessage());
        }
    }

    private static JsonNode readTree(String message) {
        if (message == null) {
            throw new InvalidEventException("The audit event is empty");
        }
        try {
            JsonNode node = JSON.readTree(message);
            if (node == null || !node.isObject()) {
                throw new InvalidEventException("The audit event is not a JSON object");
            }
            return node;
        } catch (JacksonException ex) {
            throw new InvalidEventException("Not JSON: " + ex.getOriginalMessage());
        }
    }

    /** The field's text, or {@code null} when it is missing. */
    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asString() : null;
    }

    private static void checkText(String field, String value, int max) {
        if (value == null) {
            return;
        }
        if (value.length() > max) {
            throw new InvalidEventException("The audit event's " + field + " is longer than " + max);
        }
        // PostgreSQL text cannot hold NUL (a payload keeps it escaped in its JSON).
        if (value.indexOf('\0') >= 0) {
            throw new InvalidEventException("The audit event's " + field + " holds a NUL character");
        }
    }

    /** Stores the event at the end of its service's chain. @return false when it was already stored */
    @Transactional
    public boolean store(BusinessAuditEventDTO event) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(?, hashtext(?))::text", String.class, LOCK_CLASS,
                event.service());
        Integer known = jdbc.queryForObject("SELECT count(*) FROM t_business_audit_event WHERE id = ?",
                Integer.class, event.id());
        if (known != null && known > 0) {
            return false;
        }
        List<Map<String, Object>> tip = jdbc.queryForList(TIP, event.service());
        long sequence = tip.isEmpty() ? 1 : ((Number) tip.getFirst().get("sequence_number")).longValue() + 1;
        String previous = tip.isEmpty() ? BusinessAuditChain.GENESIS : (String) tip.getFirst().get("current_hash");
        Instant received = Instant.now().truncatedTo(ChronoUnit.MICROS);
        jdbc.update(INSERT, event.id(), event.service(), sequence, event.event(), event.resource(), event.payload(),
                event.username(), event.agent(), event.result(), event.traceId(),
                Timestamp.from(event.occurredAt()), Timestamp.from(received), previous,
                chain.hash(previous, sequence, event, received));
        return true;
    }
}
