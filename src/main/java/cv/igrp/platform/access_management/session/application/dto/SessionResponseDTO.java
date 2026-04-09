package cv.igrp.platform.access_management.session.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import cv.igrp.platform.access_management.shared.application.dto.CodeDescriptionDTO;
import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;
import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import cv.igrp.platform.access_management.shared.application.dto.RoleDepartmentDTO;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.session.domain.constants.SessionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@IgrpDTO
public class SessionResponseDTO {

    private UUID sessionId;
    private SessionStatus status;
    private Instant startedAt;
    private Instant lastSeenAt;
    private Instant expiresAt;
    private Instant endedAt;
    private String closedReason;
    private String closedBy;
    private String clientIp;
    private String deviceId;
    private IGRPUserDTO userProfile;
    private RoleDepartmentDTO currentActiveRole;
    private List<RoleDepartmentDTO> roles;
    private List<CodeDescriptionDTO> departments;

    /**
     * Checks if the session is currently active
     */
    public boolean isActive() {
        return SessionStatus.ACTIVE.equals(status) && 
               expiresAt != null && 
               expiresAt.isAfter(Instant.now());
    }

    /**
     * Gets the remaining time until session expiration in seconds
     */
    public long getRemainingSeconds() {
        if (expiresAt == null) {
            return 0;
        }
        return Math.max(0, expiresAt.getEpochSecond() - Instant.now().getEpochSecond());
    }

    /**
     * Checks if the session is expired
     */
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }
}
