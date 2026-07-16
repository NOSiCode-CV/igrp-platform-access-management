package cv.igrp.platform.access_management.shared.api.audit;

import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Guards the {@code startDate}/{@code endDate} filter contract shared by the raw
 * audit list and the report/export endpoints.
 *
 * <p>An inverted range ({@code startDate > endDate}) can never match a row, so
 * silently returning an empty page hides a caller bug. Both audit surfaces reject
 * it with a 400 instead.
 */
final class AuditDateRange {

    private static final String INVERTED_RANGE =
            "startDate must be before or equal to endDate";

    private AuditDateRange() {
    }

    /** Both bounds optional (raw audit list); only an inverted pair is rejected. */
    static void require(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw IgrpResponseStatusException.badRequest(INVERTED_RANGE);
        }
    }

    /** Both bounds required by the framework binder (reports/exports). */
    static void require(Instant startDate, Instant endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw IgrpResponseStatusException.badRequest(INVERTED_RANGE);
        }
    }
}
