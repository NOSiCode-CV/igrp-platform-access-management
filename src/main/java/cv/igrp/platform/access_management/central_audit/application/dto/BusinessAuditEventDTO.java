package cv.igrp.platform.access_management.central_audit.application.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A business audit event as a service's starter records it (v3-22, #32): what
 * the relay publishes on {@code igrp.audit}, and what the central store keeps.
 *
 * @param payload the event's payload as JSON text, exactly as received
 */
public record BusinessAuditEventDTO(UUID id, String event, String resource, String payload, String username,
                                    String service, String agent, String result, String traceId,
                                    Instant occurredAt, String act) {
    public BusinessAuditEventDTO(UUID id, String event, String resource, String payload, String username,
                                String service, String agent, String result, String traceId, Instant occurredAt) {
        this(id, event, resource, payload, username, service, agent, result, traceId, occurredAt, null);
    }
}
