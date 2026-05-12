package cv.igrp.platform.access_management.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

import static cv.igrp.platform.access_management.shared.infrastructure.service.ConfigurationService.SUPER_ADMIN_ROLE;

/**
 * Two-chain security config:
 *  - Order(1) : M2M chain for /api/m2m/**
 *  - Order(2) : OAuth2 resource server for everything else
 */
@Configuration
@Profile("!basic-auth")
public class OAuth2SecurityConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OAuth2SecurityConfiguration.class);

    private final Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter;
    private final SessionEnforcementFilter sessionEnforcementFilter;
    private final M2MTokenRejectionFilter m2mTokenRejectionFilter;

    @Value("${igrp.access.m2m.sync-token:}")
    private String machineAuthToken;

    public OAuth2SecurityConfiguration(Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter,
                                       SessionEnforcementFilter sessionEnforcementFilter,
                                       M2MTokenRejectionFilter m2mTokenRejectionFilter) {
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
        this.sessionEnforcementFilter = sessionEnforcementFilter;
        this.m2mTokenRejectionFilter = m2mTokenRejectionFilter;
    }

    /**
     * Filter that authenticates M2M requests using a static token header.
     */
    private class MachineAuthFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
                throws ServletException, IOException {

            if (!request.getRequestURI().startsWith("/api/m2m/")) {
                filterChain.doFilter(request, response);
                return;
            }

            String client = request.getHeader("X-Machine-Service-ID");
            String header = request.getHeader("X-Machine-Auth-Token");

            System.out.println("X-Machine-Service-ID: " + client);
            System.out.println("X-Machine-Auth-Token: " + header);
            System.out.println("igrp.access.m2m.sync-token: " + machineAuthToken);

            if (header == null || !header.equals(machineAuthToken)) {
                log.warn("[M2M] Unauthorized access: missing or invalid authentication");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("Unauthorized: Invalid or missing machine-to-machine authentication token.");
                return;
            }

            var authority = new SimpleGrantedAuthority("ROLE_M2M");
            var principal = new User(
                    (client != null && !client.isBlank()) ? client : "m2m-client",
                    "N/A",
                    Collections.singletonList(authority)
            );
            var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            // Persist the context
            new RequestAttributeSecurityContextRepository().saveContext(context, request, response);

            log.info("[M2M] Authenticated machine client: {}", principal.getUsername());

            filterChain.doFilter(request, response);
        }
    }

    // --- Security chain 1: machine-to-machine endpoints ---
    @Bean
    @Order(1)
    public SecurityFilterChain m2mSecurityFilterChain(HttpSecurity http) throws Exception {
        var securityContextRepository = new RequestAttributeSecurityContextRepository();

        http.securityMatcher("/api/m2m/**")
                .csrf(AbstractHttpConfigurer::disable)
                .securityContext(ctx -> ctx
                        .securityContextRepository(securityContextRepository)
                )
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .addFilterBefore(new MachineAuthFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // --- Security chain 2: OAuth2 resource server for other endpoints ---
    @Bean
    @Order(2)
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
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)
                ))
                // Phase G1 / FR-13 — reject M2M-shaped JWTs (sid-less, sub == client_id)
                // immediately after authentication, BEFORE SessionEnforcementFilter,
                // so that client_credentials tokens cannot reach the permission cache
                // and trigger Integer.parseInt(sub) crashes.
                .addFilterAfter(m2mTokenRejectionFilter, BearerTokenAuthenticationFilter.class)
                // Phase C1 — enforce session state on every authenticated request,
                // immediately after the bearer token is authenticated.
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
     * Phase G1 / FR-13 — suppress Spring Boot's default auto-registration of
     * {@link M2MTokenRejectionFilter} as a global servlet filter. Same rationale
     * as {@link #sessionEnforcementFilterRegistration()}: if the filter also
     * ran at the embedded-container level (before the security chain has
     * populated {@link SecurityContextHolder}) {@link OncePerRequestFilter}
     * would no-op the in-chain registration and admit M2M tokens to user
     * endpoints. The filter is registered only inside the OAuth2 chain via
     * {@code addFilterAfter(BearerTokenAuthenticationFilter)}.
     */
    @Bean
    public FilterRegistrationBean<M2MTokenRejectionFilter> m2mTokenRejectionFilterRegistration() {
        FilterRegistrationBean<M2MTokenRejectionFilter> registration =
                new FilterRegistrationBean<>(m2mTokenRejectionFilter);
        registration.setEnabled(false);
        return registration;
    }

}
