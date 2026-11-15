package cv.igrp.platform.access_management.central_audit.application.dto;

import java.time.Instant;
import java.util.UUID;

/** A stored business audit event, with its place in its service's chain (v3-22, #32). */
public record BusinessAuditRowDTO(UUID id, String service, long sequenceNumber, String event, String resource,
                                  String payload, String username, String agent, String result, String traceId,
                                  Instant occurredAt, Instant receivedAt) {
}
