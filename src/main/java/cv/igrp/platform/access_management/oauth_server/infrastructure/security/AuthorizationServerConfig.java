package cv.igrp.platform.access_management.oauth_server.infrastructure.security;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

@Configuration
@Profile("!basic-auth")
public class AuthorizationServerConfig {

    @Bean
    @Order(0)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http,
                                                                      IgrpOidcUserService igrpOidcUserService) throws Exception {
        OAuth2AuthorizationServerConfigurer authServerConfigurer = new OAuth2AuthorizationServerConfigurer();
        var authorizationEndpoints = new OrRequestMatcher(
                AntPathRequestMatcher.antMatcher("/oauth2/authorize"),
                AntPathRequestMatcher.antMatcher("/oauth2/authorization/**"),
                AntPathRequestMatcher.antMatcher("/oauth2/token"),
                AntPathRequestMatcher.antMatcher("/oauth2/jwks"),
                AntPathRequestMatcher.antMatcher("/oauth2/revoke"),
                AntPathRequestMatcher.antMatcher("/oauth2/introspect"),
                AntPathRequestMatcher.antMatcher("/connect/**"),
                AntPathRequestMatcher.antMatcher("/.well-known/**"),
                AntPathRequestMatcher.antMatcher("/login/**"),
                AntPathRequestMatcher.antMatcher("/userinfo")
        );

        http.securityMatcher(authorizationEndpoints)
                .with(authServerConfigurer, cfg -> cfg.oidc(Customizer.withDefaults()))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                /*
				.exceptionHandling(ex -> ex.authenticationEntryPoint(
                        new LoginUrlAuthenticationEntryPoint("/oauth2/authorization/external-idp")))
                */
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
