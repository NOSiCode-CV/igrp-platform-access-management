package cv.igrp.platform.access_management.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

import static cv.igrp.platform.access_management.shared.infrastructure.service.ConfigurationService.SUPER_ADMIN_ROLE;
import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
@Profile("basic-auth") // Only active if 'basic-auth' profile is enabled
public class BasicAuthSecurityConfiguration {

    private static final Logger log = LoggerFactory.getLogger(BasicAuthSecurityConfiguration.class);

    @Value("${igrp.access.m2m.sync-token:}")
    private String machineAuthToken;

    /**
     * Filter that authenticates M2M requests using a static token header.
     */
    private class MachineAuthFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
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
                .addFilterBefore(new BasicAuthSecurityConfiguration.MachineAuthFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // --- Security chain 2: Basic Auth for other endpoints ---
    @Bean
    @Order(2)
    public SecurityFilterChain basicAuthFilterChain(HttpSecurity http) throws Exception {

        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new OncePerRequestFilter() {
                    @Override
                    protected void doFilterInternal(HttpServletRequest request,
                                                    HttpServletResponse response,
                                                    FilterChain filterChain) throws ServletException, IOException {

                        var authentication = SecurityContextHolder.getContext().getAuthentication();
                        if (authentication != null && authentication.getAuthorities() != null) {
                            boolean isSuperAdmin = authentication.getAuthorities()
                                    .stream()
                                    .anyMatch(a -> a.getAuthority().equals(SUPER_ADMIN_ROLE));

                            if (isSuperAdmin) {
                                // Skip authorization checks — fully allow request
                                filterChain.doFilter(request, response);
                                return;
                            }
                        }

                        filterChain.doFilter(request, response);
                    }
                }, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                                "/swagger-resources/**", "/webjars/**", "/actuator/**", "/api/m2m/**").permitAll()
                        .anyRequest().authenticated()
                )
                .httpBasic(withDefaults())
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
}