package cv.igrp.platform.access_management.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${igrp.access.m2m.sync-token:}")
    private String machineAuthToken;

    public OAuth2SecurityConfiguration(Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter) {
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
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
                .cors(cors -> cors.configurationSource(_ -> {
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

}