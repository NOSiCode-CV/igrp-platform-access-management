package cv.igrp.platform.access_management.session.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response payload for {@code GET /api/session/check}. Combines the JWT validity
 * (already enforced by {@code SessionEnforcementFilter} when reachable) with the
 * server-side {@code SessionEntity} state, so the FE can render a unified
 * "is my session still alive?" answer in a single call.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@IgrpDTO
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionCheckResponseDTO {

    private boolean valid;
    private UUID sid;
    private String sub;
    private Instant expiresAt;
    private Instant lastSeenAt;
    private Instant idleTimeoutAt;
    private Instant absoluteExpiryAt;
    private String reason;
}
