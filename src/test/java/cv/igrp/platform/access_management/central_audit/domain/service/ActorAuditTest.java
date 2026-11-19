package cv.igrp.platform.access_management.central_audit.domain.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActorAuditTest {
    private static final String EVENT = """
            {"id":"11111111-1111-1111-1111-111111111111","event":"read","resource":"invoices/1",
             "username":"user","service":"billing","agent":"agent-a","result":"SUCCESS",
             "traceId":"trace","occurredAt":"2026-11-19T10:00:00Z"%s}
            """;

    @Test void acceptsLegacyEventsAndHashesActorChainWhenPresent() {
        var legacy = BusinessAuditStore.parse(EVENT.formatted(""));
        assertNull(legacy.act());
        var chained = BusinessAuditStore.parse(EVENT.formatted(
                ",\"act\":{\"sub\":\"agent-a\",\"act\":{\"sub\":\"agent-b\"}}"));
        assertTrue(chained.act().contains("agent-b"));
        var hash = new BusinessAuditChain("test-secret");
        assertNotEquals(hash.hash("GENESIS", 1, legacy, legacy.occurredAt()),
                hash.hash("GENESIS", 1, chained, chained.occurredAt()));
        var tampered = BusinessAuditStore.parse(EVENT.formatted(
                ",\"act\":{\"sub\":\"agent-a\",\"act\":{\"sub\":\"changed\"}}"));
        assertNotEquals(hash.hash("GENESIS", 1, chained, chained.occurredAt()),
                hash.hash("GENESIS", 1, tampered, tampered.occurredAt()));
    }

    @Test void rejectsAgentMismatch() {
        assertThrows(BusinessAuditStore.InvalidEventException.class, () ->
                BusinessAuditStore.parse(EVENT.formatted(",\"act\":{\"sub\":\"other\"}")));
    }
}
