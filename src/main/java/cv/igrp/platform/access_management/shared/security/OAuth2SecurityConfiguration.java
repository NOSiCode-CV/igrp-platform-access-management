package cv.igrp.platform.access_management.shared.security;

import cv.igrp.framework.auth.core.route.RouteSecurity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * AM's resource-server chains (#36). AM protects its own routes as every
 * service does, with the 0.3 starter's route security: every route needs its
 * permission key, decided per request by the starter's route filter, and the
 * public routes are {@code igrp.security.public-paths}.
 *
 * <ol>
 *   <li>{@link #selfServiceSecurityFilterChain}: the documented self-service
 *       routes ({@link SelfServicePaths}), which any signed-in user reaches
 *       with no key. The onboarding ones also admit temporary users.</li>
 *   <li>{@link #oauth2SecurityFilterChain}: every other request, through
 *       {@link RouteSecurity}; {@code /api/m2m/**} and the route
 *       registration take service-account tokens only.</li>
 * </ol>
 * Both enforce the session ({@link SessionEnforcementFilter}), which skips
 * {@code /api/m2m/**} and the route registration, where service accounts call
 * with sid-less client_credentials tokens.
 */
@Configuration
@Profile("!basic-auth")
public class OAuth2SecurityConfiguration {

    private final Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter;
    private final SessionEnforcementFilter sessionEnforcementFilter;
    private final UserStatusGuard userStatusGuard;

    /**
     * OWASP A01/A05 — explicit CORS origin allowlist.
     *
     * <p>Configured via {@code igrp.security.cors.allowed-origins} (comma-separated).
     * The wildcard {@code *} is NOT accepted because the application sets
     * {@code allowCredentials=true}; browsers reject {@code Origin: *} with
     * credentials. Provide the exact front-end origin(s) through the environment
     * variable {@code IGRP_CORS_ALLOWED_ORIGINS}.
     */
    @Value("${igrp.security.cors.allowed-origins:http://localhost:3000,http://localhost:8080}")
    private String corsAllowedOriginsRaw;

    public OAuth2SecurityConfiguration(Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter,
                                       SessionEnforcementFilter sessionEnforcementFilter,
                                       UserStatusGuard userStatusGuard) {
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
        this.sessionEnforcementFilter = sessionEnforcementFilter;
        this.userStatusGuard = userStatusGuard;
    }

    /**
     * Phase G3 — the endpoints that admit
     * {@link cv.igrp.platform.access_management.shared.application.constants.Status#TEMPORARY}
     * users so they can complete onboarding. The other self-service routes
     * only need a signed-in caller, as before #36.
     */
    private static final String[] TEMPORARY_ALLOWED_PATHS = {
            "/api/users/me",
            "/api/users/invite/response",
            "/api/users/invite/validate-email",
            "/api/users/invite/validate-otp"
    };

    /** The self-service routes: signed in, no permission key. */
    @Bean
    @Order(1)
    public SecurityFilterChain selfServiceSecurityFilterChain(HttpSecurity http) {
        common(http.securityMatcher(SelfServicePaths.MATCHER))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(TEMPORARY_ALLOWED_PATHS).access((authSupplier, ctx) ->
                                new AuthorizationDecision(userStatusGuard.requiresActiveOrTemporary(authSupplier.get())))
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
    }

    /** Every other request: the starter's route security (401 anonymous, 403 without the route's key). */
    @Bean
    @Order(2)
    public SecurityFilterChain oauth2SecurityFilterChain(HttpSecurity http, RouteSecurity routeSecurity) {
        // The machine endpoints take service-account tokens only, as in 0.2: a
        // user holding their key is still refused. Then the starter's rules.
        common(http).authorizeHttpRequests(auth -> auth
                .requestMatchers(MACHINE_PATHS).access((authSupplier, ctx) ->
                        new AuthorizationDecision(isServiceAccount(authSupplier.get()))));
        routeSecurity.apply(http);
        return http.build();
    }

    /** {@code /api/m2m/**} and the route registration: service accounts only. */
    private static final String[] MACHINE_PATHS = {"/api/m2m/**", RouteRegistrationPaths.REGISTER};

    static boolean isServiceAccount(Authentication authentication) {
        return authentication instanceof JwtAuthenticationToken token
                && ServiceAccountTokenClaims.PRINCIPAL_TYPE_SERVICE_ACCOUNT.equals(
                token.getToken().getClaimAsString(ServiceAccountTokenClaims.CLAIM_PRINCIPAL_TYPE));
    }

    private HttpSecurity common(HttpSecurity http) {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Phase C1 — enforce session state on every authenticated request,
                // immediately after the bearer token is authenticated.
                .addFilterAfter(sessionEnforcementFilter, BearerTokenAuthenticationFilter.class)
                // OWASP A01/A05 — CORS allowlist sourced from configuration.
                .cors(cors -> cors.configurationSource(corsConfigurationSource()));
    }

    @Bean
    public GrantedAuthorityDefaults grantedAuthorityDefaults() {
        return new GrantedAuthorityDefaults(""); // no prefix
    }

    /**
     * Suppress Spring Boot's default auto-registration of the {@code @Component}
     * filter as a global servlet filter. Without this, the same filter instance
     * would also run at the embedded-container level (before the security chain
     * has populated {@link org.springframework.security.core.context.SecurityContextHolder}),
     * mark the request as already-filtered via {@link org.springframework.web.filter.OncePerRequestFilter},
     * and cause the in-chain {@code .addFilterAfter(...)} registration to no-op —
     * letting sid-less JWTs reach controllers. We register it only inside the
     * OAuth2 chain (see {@link #oauth2SecurityFilterChain(HttpSecurity)}), where
     * {@code BearerTokenAuthenticationFilter} has just authenticated the JWT.
     */
    @Bean
    public FilterRegistrationBean<SessionEnforcementFilter> sessionEnforcementFilterRegistration() {
        FilterRegistrationBean<SessionEnforcementFilter> registration =
                new FilterRegistrationBean<>(sessionEnforcementFilter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * OWASP A01/A05 — CORS policy with an explicit origin allowlist.
     *
     * <p>Origins are read from {@code igrp.security.cors.allowed-origins}
     * (comma-separated, env {@code IGRP_CORS_ALLOWED_ORIGINS}). Wildcards are
     * not accepted — every origin must be an exact scheme+host+port value.
     * {@code allowCredentials} is kept {@code true} so the browser sends
     * cookies / Authorization headers across origins, but only to the listed
     * origins.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> allowedOrigins = Arrays.stream(corsAllowedOriginsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With",
                "Accept", "Origin", "Cache-Control"));
        configuration.setExposedHeaders(List.of("Authorization", "Content-Disposition"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

}
