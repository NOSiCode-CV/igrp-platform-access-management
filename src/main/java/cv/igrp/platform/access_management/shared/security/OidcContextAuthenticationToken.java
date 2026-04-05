package cv.igrp.platform.access_management.shared.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;

/**
 * Custom AuthenticationToken that surfaces IgrpOidcUser as the Principal,
 * rather than surfacing a raw Jwt. This enables native @AuthenticationPrincipal bindings.
 */
public class OidcContextAuthenticationToken extends AbstractAuthenticationToken {

    private final IgrpOidcUser principal;
    private final Jwt credentials;

    public OidcContextAuthenticationToken(IgrpOidcUser principal, Jwt jwt, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.credentials = jwt;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return credentials;
    }

    @Override
    public IgrpOidcUser getPrincipal() {
        return principal;
    }

    public Jwt getJwt() {
        return credentials;
    }
}
