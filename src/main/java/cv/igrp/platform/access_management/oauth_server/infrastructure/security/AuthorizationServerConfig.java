package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.http.HttpStatus;

/**
 * Highest-priority security chain for the OAuth2 authorization server.
 *
 * <p>Handles {@code /oauth2/**}, OIDC discovery and user-info endpoints, and
 * the federated {@code /login/oauth2/**} flow. All other application traffic
 * continues to be handled by the existing resource-server and M2M chains.
 */
@Configuration
@Profile("!basic-auth")
public class AuthorizationServerConfig {

    @Bean
    @Order(0)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http,
                                                                      IgrpOidcUserService igrpOidcUserService) throws Exception {
        OAuth2AuthorizationServerConfigurer authServerConfigurer = new OAuth2AuthorizationServerConfigurer();
        http.securityMatcher(authServerConfigurer.getEndpointsMatcher())
                .with(authServerConfigurer, cfg -> cfg.oidc(Customizer.withDefaults()))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        new MediaTypeRequestMatcher(MediaType.APPLICATION_JSON)));

        return http.build();
    }

    /**
     * Login chain handling the federated OIDC flow to the external IdP and
     * optional form-login fallback. Enabled only when the external IdP is
     * configured (keeps local dev clean when no Keycloak is present).
     */
    @Bean
    @Order(-10)
    @ConditionalOnProperty(prefix = "igrp.oauth.external-idp", name = "enabled", havingValue = "true")
    public SecurityFilterChain oauthLoginSecurityFilterChain(HttpSecurity http,
                                                             IgrpOidcUserService igrpOidcUserService) throws Exception {
        http.securityMatcher("/login/**", "/oauth2/authorization/**", "/oauth2/authorize", "/login/oauth2/code/**")
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2Login(oauth2 -> oauth2.userInfoEndpoint(userInfo ->
                        userInfo.oidcUserService(igrpOidcUserService)));
        return http.build();
    }

    /**
     * Ensure Spring provides a default {@link OidcUserService} bean; the
     * IgrpOidcUserService extends it for custom provisioning logic.
     */
    @Bean
    public OidcUserService defaultOidcUserService() {
        return new OidcUserService();
    }

    @Bean
    public PasswordEncoder oauthPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
