package cv.igrp.platform.access_management.shared.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;


/**
 * Helper class for extracting information from the authentication token in the security context.
 */
@Component
public class AuthenticationHelper {

    /**
     * Retrieves the preferred username from the JWT token in the security context.
     *
     * @return preferred username
     * @throws IllegalStateException if the JWT token is not found in the security context
     */
    public String getPreferredUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getClaimAsString("preferred_username");
        }
        throw new IllegalStateException("JWT token not found in security context");
    }

    /**
     * Retrieves the JWT token value from the security context.
     *
     * @return JWT token
     * @throws IllegalStateException if the JWT token is not found in the security context
     */
    public String getToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getTokenValue();
        }
        throw new IllegalStateException("JWT token not found in security context");
    }

    /**
     * Retrieves the JWT token value from the security context as a bearer token.
     *
     * @return JWT token
     * @throws IllegalStateException if the JWT token is not found in the security context
     */
    public String getBearerToken() {
        return "Bearer " + this.getToken();
    }
}
