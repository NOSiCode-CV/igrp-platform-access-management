package cv.igrp.platform.access_management.oauth_server.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceAccountRequestDTO {

    /** Bounded to match {@code t_service_account.name} so an overlong value is a
     *  400 with a field-level message rather than a database error at flush. */
    @NotBlank(message = "name is required")
    @Size(max = 180, message = "name must be at most 180 characters")
    private String name;

    @Size(max = 500, message = "description must be at most 500 characters")
    private String description;

    private boolean active = true;

    @NotNull(message = "oauthClientId is required")
    private UUID oauthClientId;

    private Integer applicationId;

    private Set<Integer> roleIds;

    /**
     * Permission ids granted directly to this service account, bypassing the
     * role layer. The effective permission set at token-issue time is the
     * union of these direct grants and the permissions inherited from
     * {@link #roleIds}.
     */
    private Set<Integer> permissionIds;
}
