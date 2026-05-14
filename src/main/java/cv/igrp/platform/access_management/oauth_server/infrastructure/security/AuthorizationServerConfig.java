package cv.igrp.platform.access_management.oauth_server.infrastructure.security;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import cv.igrp.platform.access_management.shared.security.IgrpOidcUser;
import cv.igrp.platform.access_management.shared.security.OidcContextAuthenticationToken;
import cv.igrp.platform.access_management.shared.security.UserProfile;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

import java.util.Collections;
import java.util.List;

@Configuration
@Profile("!basic-auth")
public class AuthorizationServerConfig {

    @Bean
    @Order(0)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http,
                                                                      IgrpOidcUserService igrpOidcUserService,
                                                                      SessionAwareIntrospector sessionAwareIntrospector,
                                                                      SessionLogoutHandler sessionLogoutHandler) throws Exception {
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
                .with(authServerConfigurer, cfg -> cfg
                        // Phase E2 — wrap the introspection response so iGRP-bound
                        // tokens with a dead session report active=false even when
                        // their JWT signature would still validate.
                        .tokenIntrospectionEndpoint(introspection ->
                                introspection.introspectionResponseHandler(sessionAwareIntrospector))
                        .oidc(oidc -> oidc
                                // Phase E3 — RP-initiated logout cascades to a
                                // SessionEntity revoke + OAuth2Authorization remove.
                                .logoutEndpoint(logout ->
                                        logout.logoutResponseHandler(sessionLogoutHandler))))
                .csrf(AbstractHttpConfigurer::disable)
                .securityContext(ctx -> ctx.securityContextRepository(new HttpSessionSecurityContextRepository()))
                .requestCache(cache -> cache.requestCache(new HttpSessionRequestCache()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
				.exceptionHandling(ex -> ex
                        // FR-20 — /connect/logout (OIDC RP-initiated logout) MUST behave
                        // consistently with every other path in this chain when called
                        // unauthenticated: a 302 to the upstream IdP, not a bare
                        // `401 WWW-Authenticate: Bearer`.
                        //
                        // Root cause: Spring Authorization Server's OidcConfigurer
                        // constructor (spring-security-oauth2-authorization-server
                        // 1.5.2, line 58) always registers OidcUserInfoEndpointConfigurer
                        // by default, even when we never call `.userInfoEndpoint(...)`.
                        // That triggers OAuth2AuthorizationServerConfigurer.init()
                        // (lines 395-403) to auto-install `oauth2ResourceServer(jwt())`,
                        // which in turn registers `BearerTokenAuthenticationEntryPoint`
                        // via `defaultAuthenticationEntryPointFor(..., preferredMatcher)`
                        // where preferredMatcher includes the `MediaType.ALL` matcher
                        // (OAuth2ResourceServerConfigurer#registerDefaultEntryPoint,
                        // line 333). A curl with no Accept header sends `*/*` → matches
                        // → Bearer entry point wins on `/connect/logout` for
                        // unauthenticated callers.
                        //
                        // Fix: register an explicit path-specific entry point for the
                        // OIDC logout endpoint AFTER Spring AS has installed its own,
                        // so the DelegatingAuthenticationEntryPoint sees ours first.
                        // This restores LoginUrl-style 302 behavior to /connect/logout
                        // without disturbing /oauth2/introspect or /oauth2/revoke (those
                        // are correctly bound to HttpStatusEntryPoint(401) by the AS
                        // configurer itself and never reach the Bearer matcher).
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/oauth2/authorization/external-idp"),
                                AntPathRequestMatcher.antMatcher("/connect/logout"))
                        .authenticationEntryPoint(
                                new LoginUrlAuthenticationEntryPoint("/oauth2/authorization/external-idp")))
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

    /**
     * Persist OAuth2 authorizations in PostgreSQL so revocations and refresh-token
     * rotations survive restarts and can be joined to {@code SessionEntity} rows.
     * The accompanying schema is provisioned by {@code DatabaseMigrationRunner}
     * before any authorization is written.
     *
     * <p>The default {@link JdbcOAuth2AuthorizationService} uses Spring Security's
     * {@code SecurityJackson2Modules}-driven {@code ObjectMapper}, whose allowlist
     * rejects classes it does not recognise — including the empty JDK collection
     * sentinels (e.g. {@code java.util.Collections$EmptySet}) that
     * {@link IgrpOidcUser} stores when its authorities/profile fields are empty.
     * We therefore install a row mapper + parameters mapper backed by an
     * {@link ObjectMapper} that registers the same modules <i>plus</i> a Mixin
     * for the empty-collection sentinels and the iGRP principal types, so a
     * superadmin-with-no-explicit-authorities token can be persisted and
     * reloaded without tripping the allowlist guard.
     */
    @Bean
    public OAuth2AuthorizationService authorizationService(JdbcTemplate jdbcTemplate,
                                                           RegisteredClientRepository registeredClientRepository,
                                                           RevocationCascadeListener revocationCascadeListener,
                                                           RefreshTokenReuseGuard refreshTokenReuseGuard) {
        JdbcOAuth2AuthorizationService delegate =
                new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);

        ObjectMapper objectMapper = authorizationStoreObjectMapper();

        JdbcOAuth2AuthorizationService.OAuth2AuthorizationRowMapper rowMapper =
                new JdbcOAuth2AuthorizationService.OAuth2AuthorizationRowMapper(registeredClientRepository);
        rowMapper.setObjectMapper(objectMapper);
        delegate.setAuthorizationRowMapper(rowMapper);

        JdbcOAuth2AuthorizationService.OAuth2AuthorizationParametersMapper parametersMapper =
                new JdbcOAuth2AuthorizationService.OAuth2AuthorizationParametersMapper();
        parametersMapper.setObjectMapper(objectMapper);
        delegate.setAuthorizationParametersMapper(parametersMapper);

        // Phase C3 — every remove() (revoke / logout / one-shot consume) cascades
        // into a SessionEntity revocation so the enforcement filter (Phase C1)
        // denies the next request carrying the same sid.
        // FR-8 — save() tombstones rotated refresh tokens; findByToken() consults
        // those tombstones on a miss so replays revoke the session and publish
        // SessionRevokedEvent before Spring AS returns invalid_grant.
        return new CascadingAuthorizationService(delegate, revocationCascadeListener, refreshTokenReuseGuard);
    }

    /**
     * Build an {@link ObjectMapper} aligned with Spring Authorization Server's
     * defaults (security modules + oauth2 authorization-server module) and
     * extended with Mixins for:
     * <ul>
     *   <li>{@link IgrpOidcUser}, {@link OidcContextAuthenticationToken},
     *       {@link UserProfile} — the iGRP-specific principal types stored on
     *       the {@code OAuth2Authorization} attributes map.</li>
     *   <li>{@code Collections.emptySet/emptyList/emptyMap} — JDK sentinel
     *       singletons that pop up whenever the principal's authorities, scopes,
     *       or claim maps are empty. Without these on the allowlist the
     *       deserializer fails with {@code IllegalArgumentException: ... is not
     *       in the allowlist}.</li>
     * </ul>
     */
    private ObjectMapper authorizationStoreObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        ClassLoader classLoader = AuthorizationServerConfig.class.getClassLoader();
        List<Module> securityModules = SecurityJackson2Modules.getModules(classLoader);
        mapper.registerModules(securityModules);
        mapper.registerModule(new OAuth2AuthorizationServerJackson2Module());

        // Allowlist the empty JDK collection sentinels via Jackson Mixin so the
        // SecurityJackson2Modules allowlist resolver admits them.
        mapper.addMixIn(Collections.emptySet().getClass(), AllowlistedClassMixin.class);
        mapper.addMixIn(Collections.emptyList().getClass(), AllowlistedClassMixin.class);
        mapper.addMixIn(Collections.emptyMap().getClass(), AllowlistedClassMixin.class);
        mapper.addMixIn(Collections.emptySortedSet().getClass(), AllowlistedClassMixin.class);
        mapper.addMixIn(Collections.emptySortedMap().getClass(), AllowlistedClassMixin.class);

        // Allowlist the iGRP custom principal types.
        mapper.addMixIn(IgrpOidcUser.class, AllowlistedClassMixin.class);
        mapper.addMixIn(OidcContextAuthenticationToken.class, AllowlistedClassMixin.class);
        mapper.addMixIn(UserProfile.class, AllowlistedClassMixin.class);

        return mapper;
    }

    /**
     * Marker Mixin used to widen the {@link SecurityJackson2Modules} allowlist
     * for empty JDK collection sentinels and the iGRP principal types. The
     * {@code @JsonTypeInfo}/{@code @JsonAutoDetect} annotations mirror those
     * applied to {@link IgrpOidcUser} so default-typing round-trips work.
     */
    @com.fasterxml.jackson.annotation.JsonTypeInfo(use = com.fasterxml.jackson.annotation.JsonTypeInfo.Id.CLASS)
    @com.fasterxml.jackson.annotation.JsonAutoDetect(
            fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY,
            getterVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE,
            isGetterVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE,
            creatorVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private abstract static class AllowlistedClassMixin {
    }
}
