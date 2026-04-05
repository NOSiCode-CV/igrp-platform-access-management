package cv.igrp.platform.access_management.security_audit.application.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Provides contextual information for security audit logging.
 * It extracts details like user ID, username, IP address, and user agent
 * from the current security context and HTTP request.
 */
@Component
public class SecurityAuditContextProvider {

    /**
     * Gathers the current security context.
     *
     * @return A map containing the current user's details and request information.
     */
    public Map<String, Object> getContext() {
        Map<String, Object> context = new HashMap<>();
        
        getAuthentication().ifPresent(auth -> {
            context.put("userId", getSub(auth)); // Assuming the name is the user ID
            context.put("username", auth.getName());
            // Add roles/profiles if available in your custom principal
        });

        getRequest().ifPresent(request -> {
            context.put("sessionId", request.getSession().getId());
            context.put("ipAddress", request.getRemoteAddr());
            context.put("userAgent", request.getHeader("User-Agent"));
            context.put("requestPath", request.getRequestURI());
            context.put("correlationId", getCorrelationId(request));
        });

        return context;
    }

    private Optional<Authentication> getAuthentication() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication());
    }

    private Optional<HttpServletRequest> getRequest() {
        return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(ServletRequestAttributes.class::isInstance)
                .map(ServletRequestAttributes.class::cast)
                .map(ServletRequestAttributes::getRequest);
    }

    private String getSub(Authentication authentication) {

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

    private String getCorrelationId(HttpServletRequest request) {
        String requestCorrelationId = request.getHeader("X-Correlation-ID");
        if (requestCorrelationId != null && !requestCorrelationId.isBlank()) {
            return requestCorrelationId;
        }

        Object correlationIdAttribute = request.getAttribute("correlationId");
        if (correlationIdAttribute instanceof String correlationId && !correlationId.isBlank()) {
            return correlationId;
        }

        return UUID.randomUUID().toString();
    }

}