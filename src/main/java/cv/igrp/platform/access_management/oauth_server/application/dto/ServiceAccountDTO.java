package cv.igrp.platform.access_management.oauth_server.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceAccountDTO {

    private UUID id;
    private String name;
    private String description;
    private boolean active;
    private UUID oauthClientId;
    private String clientId;
    private Integer applicationId;
    private String applicationCode;
    /**
     * Roles held, as id/code/department triples. Prefer this over {@link #roleIds}
     * and {@link #roleCodes}: those are separate sets and cannot be paired by
     * position. Ordered by id.
     */
    private List<ServiceAccountRoleDTO> roles;
    /**
     * Directly-granted permissions, as id/name pairs. Prefer this over
     * {@link #permissionIds} and {@link #permissionNames} for the same reason.
     * Ordered by id.
     */
    private List<ServiceAccountPermissionDTO> permissions;

    /** @deprecated unpairable with {@link #roleCodes}; use {@link #roles}. Kept for compatibility. */
    @Deprecated(since = "0.2.0-beta")
    private Set<Integer> roleIds;
    /** @deprecated unpairable with {@link #roleIds}; use {@link #roles}. Kept for compatibility. */
    @Deprecated(since = "0.2.0-beta")
    private Set<String> roleCodes;
    /** @deprecated unpairable with {@link #permissionNames}; use {@link #permissions}. Kept for compatibility. */
    @Deprecated(since = "0.2.0-beta")
    private Set<Integer> permissionIds;
    /** @deprecated unpairable with {@link #permissionIds}; use {@link #permissions}. Kept for compatibility. */
    @Deprecated(since = "0.2.0-beta")
    private Set<String> permissionNames;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
