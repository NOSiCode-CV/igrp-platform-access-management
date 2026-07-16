package cv.igrp.platform.access_management.security_audit.application.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Provides contextual information for security audit logging.
 * It extracts details like user ID, username, IP address, and user agent
 * from the current security context and HTTP request.
 */
@Component
public class SecurityAuditContextProvider {

    /** Authority prefix applied to role codes by {@code IgrpJwtAuthenticationConverter}. */
    private static final String ROLE_PREFIX = "ROLE_";

    /** Matches the {@code access_role} column width (V11_1). */
    private static final int ACCESS_ROLE_MAX_LENGTH = 255;

    /**
     * Holds a pre-resolved context snapshot for the current thread. Set by the
     * settings-audit executor's task decorator so that audit writes running on a
     * worker thread (via {@code @Async}) still see the IP address, user agent and
     * user identity captured on the originating request thread — where the servlet
     * request and security context are actually available. See
     * {@link #captureContext()} and {@link #withCapturedContext(Runnable)}.
     */
    private static final ThreadLocal<Map<String, Object>> SNAPSHOT = new ThreadLocal<>();

    /**
     * Gathers the current security context.
     *
     * <p>If a snapshot has been bound to this thread (async path), it is returned
     * instead of resolving from the thread-bound servlet/security holders — which
     * are empty on a worker thread.
     *
     * @return A map containing the current user's details and request information.
     */
    public Map<String, Object> getContext() {
        Map<String, Object> snapshot = SNAPSHOT.get();
        if (snapshot != null) {
            return new HashMap<>(snapshot);
        }

        Map<String, Object> context = new HashMap<>();

        getAuthentication().ifPresent(auth -> {
            context.put("userId", getSub(auth));
            context.put("username", getUsername(auth));
            context.put("accessRole", getAccessRole(auth));
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

    /**
     * Resolves the current request/security context into an immutable snapshot
     * <em>now</em>, on the calling thread. Intended to be invoked on the request
     * thread (e.g. inside a {@code TaskDecorator}) before handing work to an async
     * worker.
     *
     * @return the resolved context values.
     */
    public Map<String, Object> captureContext() {
        return getContext();
    }

    /**
     * Wraps {@code task} so that, when it runs (potentially on another thread), the
     * given {@code snapshot} is bound as the ambient audit context for the duration
     * of the run and cleared afterwards. The snapshot is captured eagerly on the
     * current (request) thread.
     *
     * @param task the work to decorate.
     * @return a runnable that installs the captured snapshot around {@code task}.
     */
    public Runnable withCapturedContext(Runnable task) {
        Map<String, Object> snapshot = captureContext();
        return () -> {
            SNAPSHOT.set(snapshot);
            try {
                task.run();
            } finally {
                SNAPSHOT.remove();
            }
        };
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

    /**
     * Human-readable actor for the audit trail and the reports.
     *
     * <p>{@code Authentication.getName()} is the {@code sub} claim — a UUID — which
     * left every report row showing an opaque id where a username belongs and made
     * the documented {@code username} filter unmatchable (R1.5.1–§1.5.3).
     *
     * <p>Resolution mirrors {@code ApplicationAuditorAware}, which stamps
     * {@code created_by} on every entity: {@code preferred_username} first (the
     * platform's own username, enriched from {@code IGRPUserEntity.username}), so
     * an audit row and an entity's {@code created_by} name the actor identically.
     * {@code email} is a further fallback for tokens issued without the profile
     * scope, and {@code sub} the last resort — an opaque id beats no actor at all.
     */
    private String getUsername(Authentication authentication) {
        Object principal = authentication.getPrincipal();

        // The resource-server chain surfaces IgrpOidcUser (see IgrpJwtAuthenticationConverter),
        // not a raw Jwt — check the OidcUser view first.
        if (principal instanceof OidcUser oidcUser) {
            String resolved = firstNonBlank(oidcUser.getPreferredUsername(), oidcUser.getEmail());
            if (resolved != null) {
                return resolved;
            }
        }

        if (principal instanceof Jwt jwt) {
            String resolved = firstNonBlank(
                    jwt.getClaimAsString("preferred_username"), jwt.getClaimAsString("email"));
            if (resolved != null) {
                return resolved;
            }
        }

        // M2M authentication (client_credentials) — the client id is the actor.
        if (principal instanceof User user) {
            return user.getUsername();
        }

        return authentication.getName();
    }

    /**
     * Role in effect for the action, for the Audit/Access reports' {@code accessRole}
     * / {@code role} column. Authorities are the user's active role codes, granted as
     * {@code ROLE_<code>} by {@code IgrpJwtAuthenticationConverter}; a user holding
     * several active roles yields all of them. Null when the principal carries none.
     */
    private String getAccessRole(Authentication authentication) {
        if (authentication.getAuthorities() == null) {
            return null;
        }
        String roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .filter(role -> !role.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.joining(", "));

        if (roles.isBlank()) {
            return null;
        }
        // access_role is VARCHAR(255); keep the column authoritative over the principal.
        return roles.length() <= ACCESS_ROLE_MAX_LENGTH ? roles : roles.substring(0, ACCESS_ROLE_MAX_LENGTH);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
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