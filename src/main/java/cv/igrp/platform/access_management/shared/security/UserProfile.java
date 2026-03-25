package cv.igrp.platform.access_management.shared.security;

import java.util.List;

/**
 * Standardized record representing a canonical User Profile extracted from OIDC Claims.
 * This ensures type safety and predictable format (CNI, CMD handling) across the application.
 */
public record UserProfile(
        String externalId,
        String issuer,
        String fullName,
        String email,
        String phone,
        String nic,
        String authMethod,
        List<String> amr
) {}
