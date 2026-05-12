package cv.igrp.platform.access_management.shared.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

/**
 * Standardized record representing a canonical User Profile extracted from OIDC Claims.
 * This ensures type safety and predictable format (CNI, CMD handling) across the application.
 *
 * <p>Jackson annotations make the record safe to deserialize inside the
 * {@link IgrpOidcUser} principal through Spring Security's
 * {@code SecurityJackson2Modules} allowlist.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserProfile(
        @JsonProperty("id") String id,
        @JsonProperty("issuer") String issuer,
        @JsonProperty("fullName") String fullName,
        @JsonProperty("email") String email,
        @JsonProperty("phone") String phone,
        @JsonProperty("nic") String nic,
        @JsonProperty("authMethod") String authMethod,
        @JsonProperty("amr") List<String> amr
) {}
