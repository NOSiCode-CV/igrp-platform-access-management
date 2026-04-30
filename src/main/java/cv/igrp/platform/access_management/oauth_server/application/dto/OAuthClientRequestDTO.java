package cv.igrp.platform.access_management.oauth_server.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthClientRequestDTO {

    @NotBlank(message = "clientId is required")
    private String clientId;

    @NotBlank(message = "clientName is required")
    private String clientName;

    private String description;

    private boolean active = true;

    /**
     * Owning application id. Optional — a client without an owning application
     * is treated as a platform-wide client (e.g. the seeded default client).
     */
    private Integer applicationId;

    @Min(value = 60, message = "accessTokenTtl must be at least 60 seconds")
    private int accessTokenTtl = 3600;

    @Min(value = 60, message = "refreshTokenTtl must be at least 60 seconds")
    private int refreshTokenTtl = 86400;

    @Min(value = 30, message = "authorizationCodeTtl must be at least 30 seconds")
    private int authorizationCodeTtl = 300;

    @NotEmpty(message = "scopes must not be empty")
    private Set<String> scopes;

    private Set<String> redirectUris;

    @NotEmpty(message = "grantTypes must not be empty")
    private Set<String> grantTypes;
}
