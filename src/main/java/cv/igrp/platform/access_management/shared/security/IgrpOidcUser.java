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

    /**
     * Identifies the AM sign-in this principal was created by (R1.6).
     *
     * <p>Minted once in {@code IgrpOidcUserService} when the federated login
     * completes, so every application that then authorizes silently
     * ({@code prompt=none}) on the same browser session reuses this same
     * instance and reports the same value. Logout invalidates the servlet
     * session, so the next sign-in mints a different one — which is precisely
     * the "one AM sign-in" boundary R1.2 defines.
     *
     * <p>Null when the principal was not built by a sign-in — notably
     * {@code IgrpJwtAuthenticationConverter}, which reconstructs one from a
     * bearer JWT on the resource-server chain. Null is also what a principal
     * deserialized from an authorization stored before this field existed
     * yields, which {@code @JsonIgnoreProperties(ignoreUnknown = true)} makes
     * harmless. Both cases mean "no session key" (R1.5), not an error.
     */
    private final String ssoLoginId;

    @JsonCreator
    public IgrpOidcUser(
            @JsonProperty("authorities") Collection<? extends GrantedAuthority> authorities,
            @JsonProperty("idToken") OidcIdToken idToken,
            @JsonProperty("userProfile") UserProfile userProfile,
            @JsonProperty("ssoLoginId") String ssoLoginId) {
        super(authorities, idToken);
        this.userProfile = userProfile;
        this.ssoLoginId = ssoLoginId;
    }

    /**
     * For principals with no sign-in behind them, such as one rebuilt from a
     * bearer token. Equivalent to passing a null login id.
     */
    public IgrpOidcUser(Collection<? extends GrantedAuthority> authorities,
                        OidcIdToken idToken,
                        UserProfile userProfile) {
        this(authorities, idToken, userProfile, null);
    }

    public UserProfile getUserProfile() {
        return userProfile;
    }

    public String getSsoLoginId() {
        return ssoLoginId;
    }
}
