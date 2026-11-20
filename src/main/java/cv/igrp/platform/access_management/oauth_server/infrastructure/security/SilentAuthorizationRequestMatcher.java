package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.Arrays;

/**
 * An authorization request with {@code prompt=none} (OIDC Core 3.1.2.1): the
 * client asks for a code only if the user already has a session here, and for
 * {@code error=login_required} otherwise, never for a login page (#46).
 *
 * <p>Spring Security 7 runs the authorization endpoint after
 * {@code AuthorizationFilter}. With {@code anyRequest().authenticated()} an
 * anonymous request is denied and sent to the IdP login before the endpoint
 * can answer {@code login_required}; Spring Authorization Server 1.x (AM 0.2)
 * ran the endpoint first. Permitting these requests lets the endpoint decide:
 * without a session it only ever returns an error to the client's registered
 * redirect URI, so nothing is issued to an anonymous caller.
 *
 * <p>Matches by path suffix, like the logout matcher, so a gateway's
 * {@code X-Forwarded-Prefix} does not hide it.
 */
public final class SilentAuthorizationRequestMatcher implements RequestMatcher {

    public static final SilentAuthorizationRequestMatcher INSTANCE = new SilentAuthorizationRequestMatcher();

    private SilentAuthorizationRequestMatcher() {
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || !uri.endsWith("/oauth2/authorize")) {
            return false;
        }
        String prompt = request.getParameter("prompt");
        return prompt != null && Arrays.asList(prompt.trim().split("\\s+")).contains("none");
    }
}
