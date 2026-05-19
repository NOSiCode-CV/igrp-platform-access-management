package cv.igrp.platform.access_management.shared.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Single OAuth2 resource-server chain. All authenticated traffic — including
 * the M2M sync endpoints under {@code /api/m2m/**} — flows through JWT
 * validation. M2M ({@code client_credentials}) tokens are accepted on
 * {@code /api/m2m/**} because both {@link M2MTokenRejectionFilter} and
 * {@link SessionEnforcementFilter} explicitly skip that prefix.
 */
@Configuration
@Profile("!basic-auth")
public class OAuth2SecurityConfiguration {

    private final Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter;
    private final SessionEnforcementFilter sessionEnforcementFilter;
    private final M2MTokenRejectionFilter m2mTokenRejectionFilter;
    private final UserStatusGuard userStatusGuard;

    public OAuth2SecurityConfiguration(Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter,
                                       SessionEnforcementFilter sessionEnforcementFilter,
                                       M2MTokenRejectionFilter m2mTokenRejectionFilter,
                                       UserStatusGuard userStatusGuard) {
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
        this.sessionEnforcementFilter = sessionEnforcementFilter;
        this.m2mTokenRejectionFilter = m2mTokenRejectionFilter;
        this.userStatusGuard = userStatusGuard;
    }

    /**
     * Phase G3 — URL-pattern guard for the four endpoints that admit
     * {@link cv.igrp.platform.access_management.shared.application.constants.Status#TEMPORARY}
     * users so they can complete onboarding.
     *
     * <p>These endpoints live on the Studio-generated {@code UserController}.
     * Studio's metadata model does not include a slot for SpEL-based
     * authorization, so a {@code @PreAuthorize} annotation placed in the
     * generated source would be wiped on the next Studio regeneration. The
     * URL-pattern rule below survives because {@code OAuth2SecurityConfiguration}
     * is hand-written.
     */
    private static final String[] TEMPORARY_ALLOWED_PATHS = {
            "/api/users/me",
            "/api/users/invite/response",
            "/api/users/invite/validate-email",
            "/api/users/invite/validate-otp"
    };

    @Bean
    public SecurityFilterChain oauth2SecurityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/swagger-resources/**",
                                "/webjars/**",
                                "/actuator/**"
                        ).permitAll()
                        // Phase G3 — these four endpoints admit ACTIVE OR TEMPORARY
                        // users so a freshly provisioned user can finish onboarding
                        // through the invitation flow. Rule lives here (not as
                        // @PreAuthorize) because UserController is Studio-generated
                        // and the metadata format has no slot for SpEL guards.
                        .requestMatchers(TEMPORARY_ALLOWED_PATHS).access((authSupplier, ctx) ->
                                new AuthorizationDecision(userStatusGuard.requiresActiveOrTemporary(authSupplier.get()))
                        )
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)
                ))
                // Phase G1 / FR-13 — reject M2M-shaped JWTs (sid-less, sub == client_id)
                // immediately after authentication, BEFORE SessionEnforcementFilter,
                // so that client_credentials tokens cannot reach the permission cache
                // and trigger Integer.parseInt(sub) crashes. The filter skips
                // /api/m2m/** internally so legitimate M2M traffic still passes.
                .addFilterAfter(m2mTokenRejectionFilter, BearerTokenAuthenticationFilter.class)
                // Phase C1 — enforce session state on every authenticated request,
                // immediately after the bearer token is authenticated. Also skips
                // /api/m2m/** internally.
                .addFilterAfter(sessionEnforcementFilter, M2MTokenRejectionFilter.class)
                .cors(cors -> cors.configurationSource(request -> {
                    CorsConfiguration configuration = new CorsConfiguration();
                    configuration.addAllowedOriginPattern("*");
                    configuration.addAllowedMethod("*");
                    configuration.addAllowedHeader("*");
                    configuration.setAllowCredentials(true);
                    return configuration;
                }));

        return http.build();
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
     * mark the request as already-filtered via {@link OncePerRequestFilter},
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
     * Phase G1 / FR-13 — suppress Spring Boot's default auto-registration of
     * {@link M2MTokenRejectionFilter} as a global servlet filter. Same rationale
     * as {@link #sessionEnforcementFilterRegistration()}: if the filter also
     * ran at the embedded-container level (before the security chain has
     * populated {@link org.springframework.security.core.context.SecurityContextHolder})
     * {@link OncePerRequestFilter} would no-op the in-chain registration and
     * admit M2M tokens to user endpoints. The filter is registered only inside
     * the OAuth2 chain via {@code addFilterAfter(BearerTokenAuthenticationFilter)}.
     */
    @Bean
    public FilterRegistrationBean<M2MTokenRejectionFilter> m2mTokenRejectionFilterRegistration() {
        FilterRegistrationBean<M2MTokenRejectionFilter> registration =
                new FilterRegistrationBean<>(m2mTokenRejectionFilter);
        registration.setEnabled(false);
        return registration;
    }

}
