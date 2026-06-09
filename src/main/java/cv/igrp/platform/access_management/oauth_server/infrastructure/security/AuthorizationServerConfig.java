package cv.igrp.platform.access_management.oauth_server.infrastructure.security;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import cv.igrp.platform.access_management.shared.security.IgrpOidcUser;
import cv.igrp.platform.access_management.shared.security.OidcContextAuthenticationToken;
import cv.igrp.platform.access_management.shared.security.UserProfile;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
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
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

@Configuration
@Profile("!basic-auth")
public class AuthorizationServerConfig {

    private static final Logger AS_CONFIG_LOG = LoggerFactory.getLogger(AuthorizationServerConfig.class);

    /**
     * Path-suffix matcher for the OIDC RP-Initiated Logout endpoint that
     * survives {@code X-Forwarded-Prefix} URI rewriting.
     *
     * <p>Behind the API gateway, {@code server.forward-headers-strategy=framework}
     * activates Spring's {@code ForwardedHeaderFilter}, which prepends the
     * {@code X-Forwarded-Prefix} value (e.g. {@code /igrp-access-management})
     * to {@link HttpServletRequest#getRequestURI()} before any security
     * filter runs. An {@link AntPathRequestMatcher} pinned to
     * {@code "/connect/logout"} then silently misses the prefixed URI, so
     * {@code permitAll} never applies, {@code anyRequest().authenticated()}
     * denies the anonymous request, and the auto-installed
     * {@code BearerTokenAuthenticationEntryPoint} (MediaType.ALL matcher,
     * installed by {@code OAuth2AuthorizationServerConfigurer}) wins over
     * our path-specific {@code LoginUrlAuthenticationEntryPoint} —
     * producing the bare 401 {@code WWW-Authenticate: Bearer} symptom
     * confirmed by live cURL on the deployed AS pod.
     *
     * <p>Suffix matching is path-prefix-agnostic and the correct check
     * regardless of how many proxies prepended segments to the URI. CSRF
     * exclusions, permitAll, and the path-specific {@code AuthenticationEntryPoint}
     * all reuse this single matcher so they cannot drift apart.
     */
    private static final RequestMatcher CONNECT_LOGOUT_MATCHER = request -> {
        String uri = request.getRequestURI();
        boolean matches = uri != null && uri.endsWith("/connect/logout");
        if (matches && AS_CONFIG_LOG.isDebugEnabled()) {
            AS_CONFIG_LOG.debug("CONNECT_LOGOUT_MATCHER matched: method={}, uri={}, contextPath={}, servletPath={}",
                    request.getMethod(), uri, request.getContextPath(), request.getServletPath());
        }
        return matches;
    };

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
                // OWASP A05 — CSRF protection is INTENTIONALLY DISABLED on this
                // chain because every endpoint it carries authenticates the
                // request via a protocol-level credential that an off-site
                // attacker cannot forge — making Spring's session-bound _csrf
                // token redundant on every path and actively harmful on POST
                // /connect/logout (browsers can't supply a token the AS only
                // issues to its own UI, so legitimate logout posts get blocked
                // alongside the imaginary forged ones).
                //
                // Per-endpoint mitigation already present:
                //   /oauth2/token,   /oauth2/revoke,   /oauth2/introspect
                //     → client_id + client_secret in body or Basic auth.
                //       Attacker has no secret → cannot forge.
                //   /oauth2/authorize, /oauth2/authorization/**
                //     → OAuth2 `state` parameter (RFC 6749 §10.12) IS the
                //       CSRF token; Spring's OAuth2 client validates it on
                //       the callback.
                //   /connect/logout
                //     → signed `id_token_hint` JWT issued by this AS; an
                //       attacker on another origin cannot forge a signature
                //       under the AS's private key.
                //   /oauth2/jwks, /.well-known/**, /userinfo
                //     → public read-only endpoints (jwks, well-known) or
                //       Bearer-authenticated (userinfo) — no state-changing
                //       cookie auth to forge.
                //
                // The only endpoint Spring AS ships that DOES need session
                // CSRF is its built-in /login form. This deployment uses
                // oauth2Login() with an external IdP (Autentika), so that
                // form is never rendered or POSTed.
                //
                // Rationale for the disable over per-path ignore: per-path
                // ignore inside ignoringRequestMatchers requires each matcher
                // to survive ForwardedHeaderFilter URI rewriting AND stay
                // synchronised with the parallel matchers in authorizeHttpRequests
                // and exceptionHandling. Three prior fix attempts
                // (89ffa1fc, 24b32d52, ...) couldn't keep them aligned in
                // production, leaving POST /connect/logout reliably 401ing
                // behind the gateway. Disabling on this chain removes the
                // entire failure surface; security posture is unchanged
                // because no endpoint here was actually relying on the
                // protection.
                .csrf(AbstractHttpConfigurer::disable)
                // Session-backed security context is required so that CSRF tokens
                // (stored in the HTTP session) survive across the login redirect.
                .securityContext(ctx -> ctx.securityContextRepository(new HttpSessionSecurityContextRepository()))
                .requestCache(cache -> cache.requestCache(new HttpSessionRequestCache()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        // OIDC RP-initiated logout authenticates via the
                        // id_token_hint query parameter (validated inside
                        // OidcLogoutEndpointFilter), not via a session cookie
                        // or Bearer token. Permitting the path here stops the
                        // auto-installed BearerTokenAuthenticationEntryPoint
                        // from intercepting unauthenticated callers with a
                        // bare 401 WWW-Authenticate: Bearer when the request's
                        // Accept header is `*/*` (curl, SPA fetch without
                        // explicit Accept) — which made the endpoint behaviour
                        // depend on whether the JSESSIONID happened to be
                        // valid at the moment of logout. The endpoint filter
                        // still runs and enforces id_token_hint validation
                        // before our SessionLogoutHandler is invoked, so this
                        // doesn't widen the auth surface.
                        .requestMatchers(CONNECT_LOGOUT_MATCHER).permitAll()
                        .anyRequest().authenticated())
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
                                CONNECT_LOGOUT_MATCHER)
                        // Same root cause as /connect/logout above (see FR-20 block):
                        // Spring AS auto-installs oauth2ResourceServer(jwt()) which
                        // wires BearerTokenAuthenticationEntryPoint with a MediaType.ALL
                        // matcher, so a browser / Postman GET to /oauth2/authorize
                        // (Accept: */*) is met with a bare 401 WWW-Authenticate: Bearer
                        // instead of the expected 302 to /oauth2/authorization/external-idp.
                        // Register the LoginUrl entry point ahead of the Bearer matcher
                        // for the browser-facing authorization endpoints so unauthenticated
                        // callers are redirected to the upstream IdP login flow.
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/oauth2/authorization/external-idp"),
                                new OrRequestMatcher(
                                        AntPathRequestMatcher.antMatcher("/oauth2/authorize"),
                                        AntPathRequestMatcher.antMatcher("/oauth2/authorization/**")
                                ))
                        .authenticationEntryPoint(
                                new LoginUrlAuthenticationEntryPoint("/oauth2/authorization/external-idp")))
				.oauth2Login(oauth2 -> oauth2.userInfoEndpoint(userInfo ->
                        userInfo.oidcUserService(igrpOidcUserService)))
                // Defence-in-depth: strip Authorization: Bearer from
                // /connect/logout before BearerTokenAuthenticationFilter
                // (auto-installed by OAuth2AuthorizationServerConfigurer) can
                // see it. RP-initiated logout authenticates via id_token_hint,
                // not via OAuth2 access tokens — but several SPA integrations
                // (NextAuth + fetch interceptors most commonly) auto-attach
                // the user's current access_token to every API call. By the
                // time the user clicks "log out" that access_token has often
                // expired or been revoked by a parallel logout attempt, the
                // Bearer filter validates and rejects with bare 401
                // WWW-Authenticate: Bearer, and the OidcLogoutEndpointFilter
                // never runs. Stripping the header here makes the endpoint
                // immune to that whole class of integration bug.
                .addFilterBefore(new StripAuthorizationHeaderForLogoutFilter(),
                        BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Wraps each {@code /connect/logout} request so the {@code Authorization}
     * header is hidden from downstream filters. See the
     * {@code addFilterBefore(...)} site above for full rationale.
     */
    private static final class StripAuthorizationHeaderForLogoutFilter extends OncePerRequestFilter {

        private static final Logger LOG =
                LoggerFactory.getLogger(StripAuthorizationHeaderForLogoutFilter.class);

        /**
         * Path-suffix match instead of {@link org.springframework.security.web.util.matcher.AntPathRequestMatcher}
         * because behind the API gateway, Spring's {@code ForwardedHeaderFilter}
         * (driven by {@code X-Forwarded-Prefix: /igrp-access-management}) rewrites
         * the request URI to include the prefix before this filter runs. An ant
         * matcher pinned to {@code "/connect/logout"} would miss
         * {@code "/igrp-access-management/connect/logout"} and the strip would
         * silently no-op — which is exactly the production symptom we saw
         * (audit log: {@code requestPath:"/igrp-access-management/error"}).
         * Endpoint-suffix matching is correct regardless of how many proxies
         * prepended path segments.
         */
        @Override
        protected void doFilterInternal(@NonNull HttpServletRequest request,
                                        @NonNull HttpServletResponse response,
                                        @NonNull FilterChain filterChain)
                throws ServletException, IOException {
            String uri = request.getRequestURI();
            if (uri != null && uri.endsWith("/connect/logout")
                    && request.getHeader("Authorization") != null) {
                LOG.debug("Stripping Authorization header from logout request: uri={}, method={}",
                        uri, request.getMethod());
                filterChain.doFilter(new AuthorizationStrippingRequestWrapper(request), response);
                return;
            }
            filterChain.doFilter(request, response);
        }

        /**
         * Hides the {@code Authorization} header from downstream filters
         * without mutating the underlying request — the original
         * {@link HttpServletRequest} is read-only at the servlet contract
         * level, so we layer a wrapper that lies on the relevant getters.
         */
        private static final class AuthorizationStrippingRequestWrapper extends HttpServletRequestWrapper {
            AuthorizationStrippingRequestWrapper(HttpServletRequest request) {
                super(request);
            }

            @Override
            public String getHeader(String name) {
                if ("authorization".equalsIgnoreCase(name)) {
                    return null;
                }
                return super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
                if ("authorization".equalsIgnoreCase(name)) {
                    return Collections.emptyEnumeration();
                }
                return super.getHeaders(name);
            }

            @Override
            public Enumeration<String> getHeaderNames() {
                Enumeration<String> original = super.getHeaderNames();
                return new Enumeration<>() {
                    private String next = advance();

                    private String advance() {
                        while (original.hasMoreElements()) {
                            String candidate = original.nextElement();
                            if (!"authorization".equalsIgnoreCase(candidate)) {
                                return candidate;
                            }
                        }
                        return null;
                    }

                    @Override
                    public boolean hasMoreElements() {
                        return next != null;
                    }

                    @Override
                    public String nextElement() {
                        String result = next;
                        next = advance();
                        return result;
                    }
                };
            }
        }
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
