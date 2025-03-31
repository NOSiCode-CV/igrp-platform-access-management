package cv.igrp.platform.access_management.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.http.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.TokenExchangeOAuth2AuthorizedClientProvider;
import org.springframework.web.cors.CorsConfiguration;

/**
 * Security configuration class for setting up OAuth2 and JWT authentication with Keycloak.
 * This class defines the security filter chain, policy enforcer filter, and JWT authentication conversion logic.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${spring.profiles.active}")
    private String activeProfile;

    @Value("${auth.jwt.issuer}")
    private String jwtIssuer;

    /**
     * Configures the security filter chain, enabling OAuth2 resource server with JWT and specifying
     * which requests require authentication.
     *
     * @param http the {@link HttpSecurity} object to configure security settings
     * @return the configured {@link SecurityFilterChain} instance
     * @throws Exception if an error occurs while configuring the security
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        /*
          Creates and configures a CORS filter.
          The filter allows requests from the specified origin, allows all headers and methods,
          and supports credentials in cross-origin requests.
        */
        http.cors(cors -> cors.configurationSource(request -> {
          var configuration = new CorsConfiguration();
          configuration.addAllowedOriginPattern(CorsConfiguration.ALL);
          configuration.addAllowedMethod(HttpMethod.GET);
          configuration.addAllowedMethod(HttpMethod.POST);
          configuration.addAllowedMethod(HttpMethod.PUT);
          configuration.addAllowedMethod(HttpMethod.PATCH);
          configuration.addAllowedMethod(HttpMethod.DELETE);
          configuration.addAllowedMethod(HttpMethod.HEAD);
          configuration.addAllowedMethod(HttpMethod.OPTIONS);
          configuration.addAllowedHeader(CorsConfiguration.ALL);
          configuration.setAllowCredentials(true);
          return configuration;
        }));

        if ("development".equals(activeProfile) || "staging".equals(activeProfile)) {
            // Disable security in development mode
            http.csrf(AbstractHttpConfigurer::disable); // Disable CSRF protection
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }

        // Configure OAuth2 Resource Server to use JWT tokens for authentication
        http.oauth2ResourceServer((oauth2ResourceServer) -> oauth2ResourceServer
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
        );

        // Configure authorization rules and policy enforcement
        http
                .authorizeHttpRequests((authorize) -> authorize
                        .requestMatchers(HttpMethod.GET).permitAll()  // Allow GET requests to specified URLs without authentication
                        .anyRequest().authenticated()  // Require authentication for all other requests
                )
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                    response.addHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"Restricted Content\"");
                    response.sendError(HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED.getReasonPhrase());
                }));

        // Set session management to stateless (no session created for API requests)
        http.sessionManagement(t -> t.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }

    /**
     * Configures a JWT authentication converter that extracts roles from the JWT and assigns them to authorities.
     *
     * @return the {@link JwtAuthenticationConverter} used to convert JWT tokens to Spring Security authentication
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        var grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        converter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return converter;
    }

    /**
     * Configures a JWT decoder to verify and decode JWT tokens.
     *
     * @return the {@link JwtDecoder} for JWT token validation
     */
    @Bean
    @Profile("!development & !staging")
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withIssuerLocation(jwtIssuer).build();
    }

    /**
     * Creates a bean for an OAuth2AuthorizedClientProvider that supports token exchange.
     *
     * <p>Token exchange allows one token to be exchanged for another,
     * typically used in scenarios where a client needs to act on behalf
     * of a user or service in a federated identity environment.</p>
     *
     * @return An instance of TokenExchangeOAuth2AuthorizedClientProvider.
    */
    @Bean
    public OAuth2AuthorizedClientProvider tokenExchange() {
        return new TokenExchangeOAuth2AuthorizedClientProvider();
    }

}