package cv.igrp.platform.access_management.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;

/**
 * AM's self-service routes (#36): any signed-in user reaches them, with no
 * permission key. They are what a user needs before, or regardless of, any
 * role: their own profile, applications, roles and permission set, the
 * invitation steps, the session endpoints, the branding every App Center
 * page loads, and the page check every frontend middleware makes (#34).
 * {@code GET /api/users/me/permissions} computes the keys, so it cannot need
 * one.
 *
 * <p>This list is the documented exception to the route filter: these paths
 * are served by AM's own chain ({@code OAuth2SecurityConfiguration}), and are
 * not registered as routes. Every other AM route needs its key.
 */
public final class SelfServicePaths {

    /** One self-service route: its method and path pattern. */
    record Entry(HttpMethod method, PathPattern pattern) {

        boolean matches(String method, String path) {
            return this.method.matches(method) && pattern.matches(PathContainer.parsePath(path));
        }
    }

    /**
     * Each one exactly, method and pattern: a wider pattern ({@code /api/users/me/**})
     * would also let {@code me} through to an administrator's {@code /api/users/{id}/...}
     * route with no key.
     */
    static final List<Entry> ENTRIES = List.of(
            route(HttpMethod.DELETE, "/api/users/me/applications/favorites/{applicationCode}"),
            route(HttpMethod.GET, "/api/global-configuration"),
            // #34: the page check, about the caller themselves.
            route(HttpMethod.POST, "/api/authorize/page"),
            route(HttpMethod.GET, "/api/session"),
            route(HttpMethod.GET, "/api/session/check"),
            route(HttpMethod.GET, "/api/users/invite/by-token/{token}"),
            route(HttpMethod.GET, "/api/users/me"),
            route(HttpMethod.GET, "/api/users/me/applications"),
            route(HttpMethod.GET, "/api/users/me/applications/favorites"),
            route(HttpMethod.GET, "/api/users/me/applications/recent"),
            route(HttpMethod.GET, "/api/users/me/applications/{applicationCode}/menus"),
            route(HttpMethod.GET, "/api/users/me/departments"),
            route(HttpMethod.GET, "/api/users/me/departments/{departmentCode}/roles"),
            route(HttpMethod.GET, "/api/users/me/permissions"),
            route(HttpMethod.GET, "/api/users/me/role/active"),
            route(HttpMethod.GET, "/api/users/me/roles"),
            route(HttpMethod.GET, "/api/users/me/roles/active"),
            route(HttpMethod.POST, "/api/session/close"),
            route(HttpMethod.POST, "/api/session/refresh"),
            route(HttpMethod.POST, "/api/session/rotate"),
            route(HttpMethod.POST, "/api/users/invite/response"),
            route(HttpMethod.POST, "/api/users/invite/validate-email"),
            route(HttpMethod.POST, "/api/users/invite/validate-otp"),
            route(HttpMethod.POST, "/api/users/me/applications/favorites/{applicationCode}"),
            route(HttpMethod.POST, "/api/users/me/applications/recent/{applicationCode}"),
            route(HttpMethod.POST, "/api/users/me/roles/active"),
            route(HttpMethod.PUT, "/api/users/me"),
            route(HttpMethod.PUT, "/api/users/me/locale"),
            route(HttpMethod.PUT, "/api/users/me/role/active"));

    /** The chain's matcher: the request is a self-service route. */
    public static final RequestMatcher MATCHER = request -> matches(request.getMethod(), pathOf(request));

    private SelfServicePaths() {
    }

    /** @param path the path pattern a route is mapped to, or a request path */
    public static boolean matches(String method, String path) {
        return ENTRIES.stream().anyMatch(entry -> entry.matches(method, path));
    }

    private static String pathOf(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    private static Entry route(HttpMethod method, String pattern) {
        return new Entry(method, PathPatternParser.defaultInstance.parse(pattern));
    }
}
