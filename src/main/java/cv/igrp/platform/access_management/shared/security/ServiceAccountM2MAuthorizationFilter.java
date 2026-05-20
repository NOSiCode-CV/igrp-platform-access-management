package cv.igrp.platform.access_management.shared.security;

import cv.igrp.framework.auth.core.authorization.model.PermissionCheckRequest;
import cv.igrp.platform.access_management.authorization.domain.service.PermissionCacheService;
import cv.igrp.platform.access_management.shared.infrastructure.authorization.permission.DepartmentPermissions;
import cv.igrp.platform.access_management.shared.infrastructure.authorization.permission.M2MPermissions;
import cv.igrp.platform.access_management.shared.infrastructure.authorization.permission.UserPermissions;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Enforces role/permission decisions for OAuth service-account tokens on the
 * generated M2M endpoints while leaving the legacy static-header M2M path
 * untouched.
 */
@Component
public class ServiceAccountM2MAuthorizationFilter extends OncePerRequestFilter {

    private static final String M2M_PREFIX = "/api/m2m/";

    private final PermissionCacheService permissionCacheService;

    public ServiceAccountM2MAuthorizationFilter(PermissionCacheService permissionCacheService) {
        this.permissionCacheService = permissionCacheService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(M2M_PREFIX);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        Jwt jwt = extractJwt(SecurityContextHolder.getContext().getAuthentication());
        if (jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String principalType = jwt.getClaimAsString(ServiceAccountTokenClaims.CLAIM_PRINCIPAL_TYPE);
        if (!ServiceAccountTokenClaims.PRINCIPAL_TYPE_SERVICE_ACCOUNT.equals(principalType)) {
            forbidden(response, "access_denied", "m2m_requires_service_account");
            return;
        }

        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            forbidden(response, "access_denied", "missing_service_account_subject");
            return;
        }

        PermissionCheckRequest permissionRequest = new PermissionCheckRequest();
        permissionRequest.setSubject(subject);
        permissionRequest.setResource(request.getRequestURI());
        permissionRequest.setAction(requiredPermission(request));

        if (!permissionCacheService.getOrLoadPermission(permissionRequest).allowed()) {
            forbidden(response, "access_denied", "service_account_permission_denied");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static String requiredPermission(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/m2m/sync/")) {
            return M2MPermissions.IGRP_M2M_SYNC;
        }
        if (uri.equals("/api/m2m/users")) {
            return UserPermissions.IGRP_USERS_LIST;
        }
        if (uri.equals("/api/m2m/departments")) {
            return DepartmentPermissions.IGRP_DEPARTMENTS_LIST;
        }
        if (uri.equals("/api/m2m/roles")) {
            return DepartmentPermissions.IGRP_DEPARTMENTS_VIEW;
        }
        return M2MPermissions.IGRP_M2M_SYNC;
    }

    private static Jwt extractJwt(Authentication authentication) {
        if (authentication instanceof OidcContextAuthenticationToken oidcToken) {
            return oidcToken.getJwt();
        }
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        return null;
    }

    private static void forbidden(HttpServletResponse response,
                                  String error,
                                  String description) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.reset();
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
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
