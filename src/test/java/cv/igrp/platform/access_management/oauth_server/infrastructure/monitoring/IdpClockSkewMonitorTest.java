package cv.igrp.platform.access_management.oauth_server.infrastructure.monitoring;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class IdpClockSkewMonitorTest {

    @Test
    void parsesRfc1123DateHeader() {
        Optional<Instant> parsed = IdpClockSkewMonitor.parseHttpDate("Tue, 01 Sep 2026 11:28:18 GMT");

        assertTrue(parsed.isPresent());
        assertEquals(Instant.parse("2026-09-01T11:28:18Z"), parsed.get());
    }

    @Test
    void ignoresMissingOrUnparsableDateHeader() {
        assertTrue(IdpClockSkewMonitor.parseHttpDate(null).isEmpty());
        assertTrue(IdpClockSkewMonitor.parseHttpDate("   ").isEmpty());
        assertTrue(IdpClockSkewMonitor.parseHttpDate("not-a-date").isEmpty());
    }

    @Test
    void skewIsPositiveWhenThisServerIsAhead() {
        long skew = IdpClockSkewMonitor.skewSeconds(
                Instant.parse("2026-09-01T11:00:00Z"),
                Instant.parse("2026-09-01T11:00:45Z"));

        assertEquals(45L, skew);
    }

    @Test
    void skewIsNegativeWhenThisServerIsBehind() {
        long skew = IdpClockSkewMonitor.skewSeconds(
                Instant.parse("2026-09-01T11:00:45Z"),
                Instant.parse("2026-09-01T11:00:00Z"));

        assertEquals(-45L, skew);
    }

    @Test
    void subSecondNoiseNeverTripsTheThreshold() {
        // HTTP Date has one-second resolution, so a 1s reading is measurement noise
        // even if someone sets the threshold to 0.
        assertFalse(IdpClockSkewMonitor.exceedsThreshold(1L, 0L));
        assertFalse(IdpClockSkewMonitor.exceedsThreshold(-1L, 0L));
    }

    @Test
    void exceedsThresholdInEitherDirection() {
        assertTrue(IdpClockSkewMonitor.exceedsThreshold(31L, 30L));
        assertTrue(IdpClockSkewMonitor.exceedsThreshold(-31L, 30L));
        assertFalse(IdpClockSkewMonitor.exceedsThreshold(30L, 30L));
        assertFalse(IdpClockSkewMonitor.exceedsThreshold(-30L, 30L));
    }

    @Test
    void theReportedOutageWouldHaveBeenAlertedOn() {
        // 17 days of drift, as in the reported incident.
        long skew = IdpClockSkewMonitor.skewSeconds(
                Instant.parse("2026-09-18T11:00:00Z"),
                Instant.parse("2026-09-01T11:00:00Z"));

        assertTrue(IdpClockSkewMonitor.exceedsThreshold(skew, 30L));
    }
}
