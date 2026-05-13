package cv.igrp.platform.access_management.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Phase G1 / FR-13 — first line of defense against M2M
 * ({@code client_credentials}) JWTs reaching user-scoped endpoints.
 *
 * <p>A live production crash was reproduced against
 * {@code https://api-demoigrp.nosi.cv/igrp-access-management} where a
 * sid-less M2M token (sub={@code "igrp-access-management"}) traveled to
 * {@code POST /api/admin/users/{id}/sessions/{sid}/kill} and exploded with
 * {@link NumberFormatException} from {@code Integer.parseInt(jwt.getSubject())}
 * inside the permission cache. This filter is positioned on the OAuth2
 * resource-server chain immediately after the {@code BearerTokenAuthenticationFilter}
 * and <b>before</b> {@link SessionEnforcementFilter}: it inspects the
 * authenticated JWT, recognizes the M2M shape (absent {@code sid} claim AND
 * {@code client_id == sub}), and rejects with HTTP 401 plus a standards-shaped
 * {@code WWW-Authenticate} challenge.
 *
 * <p>Path-based skip mirrors {@link SessionEnforcementFilter}: anything that is
 * legitimately served to non-user principals (M2M endpoints, swagger/actuator,
 * the authorization-server itself) bypasses the filter so M2M clients can
 * still talk to {@code /api/m2m/**} etc.
 *
 * <p>Like {@link SessionEnforcementFilter} this {@code @Component} must be
 * opted out of Spring Boot's servlet-level auto-registration via a
 * {@code FilterRegistrationBean} with {@code setEnabled(false)} in
 * {@link OAuth2SecurityConfiguration} — otherwise it would also run at the
 * embedded-container level before the security chain has populated the
 * {@code SecurityContext}, marking the request as already-filtered and
 * neutering the in-chain registration.
 */
@Component
public class M2MTokenRejectionFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(M2MTokenRejectionFilter.class);

    private static final String CLAIM_SID = "sid";
    private static final String CLAIM_CLIENT_ID = "client_id";

    private static final List<String> SKIPPED_PREFIXES = List.of(
            "/api/m2m/",
            "/api/session/check",
            "/v3/api-docs",
            "/swagger-ui",
            "/swagger-resources",
            "/webjars",
            "/actuator",
            "/oauth2/",
            "/connect/",
            "/.well-known/",
            "/login",
            "/userinfo"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        for (String prefix : SKIPPED_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = extractJwt(authentication);

        if (jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String sid = jwt.getClaimAsString(CLAIM_SID);
        if (sid != null && !sid.isBlank()) {
            // Real user session token — defer to SessionEnforcementFilter and beyond.
            filterChain.doFilter(request, response);
            return;
        }

        String sub = jwt.getSubject();
        String clientId = jwt.getClaimAsString(CLAIM_CLIENT_ID);
        if (sub != null && sub.equals(clientId)) {
            // Canonical M2M shape: no sid, sub == client_id. Reject immediately.
            LOGGER.warn("M2M token rejected on user-scoped path {} (sub={}, client_id={})",
                    request.getRequestURI(), sub, clientId);
            unauthorized(response, "invalid_token", "m2m_token_not_allowed");
            return;
        }

        // Sid-less but not the M2M shape — let SessionEnforcementFilter produce
        // its own missing_sid challenge so the failure mode stays consistent.
        filterChain.doFilter(request, response);
    }

    private static Jwt extractJwt(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        return null;
    }

    /**
     * Commit a 401 response immediately so no downstream filter or entry point
     * can rewrite the {@code WWW-Authenticate} header — same defensive pattern
     * as {@link SessionEnforcementFilter#unauthorized(HttpServletResponse, String, String)}
     * introduced by commit 04a59662.
     */
    private static void unauthorized(HttpServletResponse response,
                                     String error,
                                     String description) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.reset();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate",
                "Bearer error=\"" + error + "\", error_description=\"" + description + "\"");
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("application/json;charset=UTF-8");
        String body = "{\"error\":\"" + error
                + "\",\"error_description\":\"" + description
                + "\"}";
        response.getWriter().write(body);
        response.getWriter().flush();
        response.flushBuffer();
    }
}
