package cv.igrp.platform.access_management.shared.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;


/**
 * Helper class for extracting information from the authentication token in the security context.
 */
@Component
public class AuthenticationHelper {

    /**
     * Retrieves the username from the current SecurityContext.
     * <p>
     * Supports both JWT-based authentication (standard OAuth2 users)
     * and machine-to-machine (M2M) authentication
     *
     * @return the username or client ID depending on the authentication type
     * @throws IllegalStateException if no authentication is found
     */
    public String getPreferredUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            throw new IllegalStateException("No authentication found in security context");
        }

        // Case 1: JWT (OAuth2 user)
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getClaimAsString("sub");
        }

        // Case 2: M2M authentication (UsernamePasswordAuthenticationToken)
        if (authentication.getPrincipal() instanceof User user) {
            return user.getUsername();
        }

        // Case 3: Fallback (any other type)
        return authentication.getName();
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
     * Retrieves the JWT token value from the security context.
     *
     * @return JWT token
     * @throws IllegalStateException if the JWT token is not found in the security context
     */
    public Jwt getJwtToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
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
