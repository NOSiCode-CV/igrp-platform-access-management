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
public class OAuthClientDTO {

    private UUID id;
    private String clientId;
    /**
     * Populated only on client creation so the caller can hand the secret
     * off to the client operator. Null on read operations.
     */
    private String clientSecret;
    private String clientName;
    private String description;
    private boolean active;
    /**
     * Whether the authorization_code flow on this client requires a PKCE
     * code_challenge. OWASP A01 default: true.
     */
    private boolean requirePkce;
    private Integer applicationId;
    private String applicationCode;
    private int accessTokenTtl;
    private int refreshTokenTtl;
    private int authorizationCodeTtl;
    private Set<String> scopes;
    private Set<String> redirectUris;
    private Set<String> postLogoutRedirectUris;
    private Set<String> grantTypes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
