package cv.igrp.platform.access_management.shared.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.Collection;

/**
 * Custom authentication token that holds the extracted canonical UserProfile.
 * This allows the application to access the normalized profile directly from the SecurityContext.
 */
public class CustomJwtAuthenticationToken extends JwtAuthenticationToken {

    private final UserProfile userProfile;

    public CustomJwtAuthenticationToken(Jwt jwt, Collection<? extends GrantedAuthority> authorities, UserProfile userProfile) {
        super(jwt, authorities);
        this.userProfile = userProfile;
    }

    public UserProfile getUserProfile() {
        return userProfile;
    }
}
