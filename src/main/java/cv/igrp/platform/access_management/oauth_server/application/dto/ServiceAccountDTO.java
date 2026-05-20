package cv.igrp.platform.access_management.oauth_server.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
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
    private Set<Integer> roleIds;
    private Set<String> roleCodes;
    /** Permission ids granted directly on the service account, bypassing the role layer. */
    private Set<Integer> permissionIds;
    /** Permission names corresponding to {@link #permissionIds}. */
    private Set<String> permissionNames;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
