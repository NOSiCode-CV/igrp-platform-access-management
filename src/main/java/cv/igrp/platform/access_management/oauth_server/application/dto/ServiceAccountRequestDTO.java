package cv.igrp.platform.access_management.oauth_server.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    @NotBlank(message = "name is required")
    private String name;

    private String description;

    private boolean active = true;

    @NotNull(message = "oauthClientId is required")
    private UUID oauthClientId;

    private Integer applicationId;

    private Set<Integer> roleIds;
}
