package cv.igrp.platform.access_management.shared.security;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import java.util.Collection;

/**
 * Custom OIDC User principal extending Spring Security's DefaultOidcUser.
 * Native representation of the incoming ID Token holding the custom normalized
 * UserProfile data directly within the Authentication Context.
 *
 * <p>Carries the Jackson annotations required by Spring Security's
 * {@code SecurityJackson2Modules} allowlist so that an {@code
 * OAuth2AuthenticationToken} holding this principal can be round-tripped
 * through the session store (HTTP session / Redis) and the persistent
 * {@code JdbcOAuth2AuthorizationService} without tripping the
 * "class is not in the allowlist" deserialization guard.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        creatorVisibility = JsonAutoDetect.Visibility.ANY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class IgrpOidcUser extends DefaultOidcUser {

    private final UserProfile userProfile;

    @JsonCreator
    public IgrpOidcUser(
            @JsonProperty("authorities") Collection<? extends GrantedAuthority> authorities,
            @JsonProperty("idToken") OidcIdToken idToken,
            @JsonProperty("userProfile") UserProfile userProfile) {
        super(authorities, idToken);
        this.userProfile = userProfile;
    }

    public UserProfile getUserProfile() {
        return userProfile;
    }
}
