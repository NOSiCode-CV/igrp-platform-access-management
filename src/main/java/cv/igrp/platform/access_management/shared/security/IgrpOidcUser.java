package cv.igrp.platform.access_management.shared.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import java.util.Collection;

/**
 * Custom OIDC User principal extending Spring Security's DefaultOidcUser.
 * Native representation of the incoming ID Token holding the custom normalized 
 * UserProfile data directly within the Authentication Context.
 */
public class IgrpOidcUser extends DefaultOidcUser {

    private final UserProfile userProfile;

    public IgrpOidcUser(Collection<? extends GrantedAuthority> authorities, OidcIdToken idToken, UserProfile userProfile) {
        super(authorities, idToken);
        this.userProfile = userProfile;
    }

    public UserProfile getUserProfile() {
        return userProfile;
    }
}
