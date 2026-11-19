package cv.igrp.platform.access_management.shared.security.integration;

import cv.igrp.framework.auth.core.route.AccessAudit;
import cv.igrp.framework.auth.core.route.PermissionLookup;
import cv.igrp.framework.auth.core.route.RouteResolver;
import cv.igrp.framework.auth.core.route.RouteSecurity;
import cv.igrp.framework.auth.core.security.PermissionAuthenticationConverter;
import cv.igrp.platform.access_management.oauth_server.infrastructure.security.UserAuthorizations;
import cv.igrp.platform.access_management.permission_fetch.application.dto.PermissionSetDTO;
import cv.igrp.platform.access_management.permission_fetch.domain.service.PermissionFetchService;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.security.AmRouteSecurityConfig;
import cv.igrp.platform.access_management.shared.security.OAuth2SecurityConfiguration;
import cv.igrp.platform.access_management.shared.security.ServiceAccountTokenClaims;
import cv.igrp.platform.access_management.shared.security.SignedInGuard;
import cv.igrp.platform.access_management.shared.security.UserStatusGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AM's security chains wired as in production ({@link OAuth2SecurityConfiguration} with the
 * starter's route security), driven through MockMvc with the full Spring Security filter chain:
 * <ul>
 *   <li>a self-service route admits a signed-in user, and refuses with 401 a user who is no
 *       longer signed in (#33: there are no sessions; signed in is an active authorization);</li>
 *   <li>{@code /api/m2m/**} takes a service account's token with the route's key, and refuses
 *       one without it and any user token (#36).</li>
 * </ul>
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = SecurityChainWiringIntegrationTest.TestApp.class)
@TestPropertySource(properties = "igrp.security.application-code=" + SecurityChainWiringIntegrationTest.APP)
class SecurityChainWiringIntegrationTest {

    static final String APP = "TEST_APP";
    private static final String PING_KEY = "test-api.m2m.ping.create";
    private static final String USER_ID = "00000000-0000-0000-0000-0000000000a1";

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private JwtDecoder jwtDecoder;
    @Autowired
    private PermissionFetchService permissionFetch;
    @Autowired
    private UserAuthorizations authorizations;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("A self-service route admits a signed-in user")
    void selfServiceSignedIn() throws Exception {
        when(jwtDecoder.decode("user")).thenReturn(userJwt("user"));
        when(authorizations.hasActive(USER_ID)).thenReturn(true);

        mockMvc.perform(get("/api/users/me/roles").header("Authorization", "Bearer user"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("After a logout or kill, a self-service route answers 401 though the token has not expired")
    void selfServiceSignedOut() throws Exception {
        when(jwtDecoder.decode("user")).thenReturn(userJwt("user"));
        when(authorizations.hasActive(USER_ID)).thenReturn(false);

        mockMvc.perform(get("/api/users/me/roles").header("Authorization", "Bearer user"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")));
    }

    @Test
    @DisplayName("A self-service route answers 401 with no token")
    void selfServiceAnonymous() throws Exception {
        mockMvc.perform(get("/api/users/me/roles")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A service account's token passes /api/m2m/** with the route's key")
    void m2mWithTheKey() throws Exception {
        when(jwtDecoder.decode("test-cc")).thenReturn(serviceAccountJwt("test-cc"));
        when(permissionFetch.fetch(any(Jwt.class), eq(APP))).thenReturn(
                new PermissionSetDTO(List.of(PING_KEY), Map.of(PING_KEY, "all"), List.of(), List.of()));

        mockMvc.perform(post("/api/m2m/ping").header("Authorization", "Bearer test-cc")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Without the route's key, a service token on /api/m2m/** is refused")
    void m2mWithoutTheKey() throws Exception {
        when(jwtDecoder.decode("test-cc")).thenReturn(serviceAccountJwt("test-cc"));
        when(permissionFetch.fetch(any(Jwt.class), eq(APP))).thenReturn(
                new PermissionSetDTO(List.of(), Map.of(), List.of(), List.of()));

        mockMvc.perform(post("/api/m2m/ping").header("Authorization", "Bearer test-cc")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A user holding the route's key is still refused on /api/m2m/**: service accounts only")
    void m2mRefusesAUser() throws Exception {
        when(jwtDecoder.decode("user")).thenReturn(userJwt("user"));
        when(permissionFetch.fetch(any(Jwt.class), eq(APP))).thenReturn(
                new PermissionSetDTO(List.of(PING_KEY), Map.of(PING_KEY, "all"), List.of(), List.of()));

        mockMvc.perform(post("/api/m2m/ping").header("Authorization", "Bearer user")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    private static Jwt userJwt(String tokenValue) {
        return jwt(tokenValue, Map.of("sub", USER_ID));
    }

    /** A client_credentials token of a service account, with its principal type. */
    private static Jwt serviceAccountJwt(String tokenValue) {
        return jwt(tokenValue, Map.of("sub", "igrp-access-management",
                ServiceAccountTokenClaims.CLAIM_PRINCIPAL_TYPE, ServiceAccountTokenClaims.PRINCIPAL_TYPE_SERVICE_ACCOUNT));
    }

    private static Jwt jwt(String tokenValue, Map<String, Object> extra) {
        Map<String, Object> claims = new HashMap<>(extra);
        claims.put("iss", "http://localhost");
        claims.put("jti", "test-jti");
        Instant now = Instant.now();
        return new Jwt(tokenValue, now, now.plusSeconds(300), Map.of("alg", "RS256"), claims);
    }

    /**
     * The production {@link OAuth2SecurityConfiguration} and AM's route security, unchanged;
     * everything else is stubbed to keep JPA, Redis and the authorization store out.
     */
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    @Import({OAuth2SecurityConfiguration.class, AmRouteSecurityConfig.class})
    static class TestApp {

        @Bean
        PermissionFetchService permissionFetchService() {
            return mock(PermissionFetchService.class);
        }

        /** The starter's lookup; AM's post-processor rebuilds it with the in-process fetch. */
        @Bean
        PermissionLookup igrpPermissionLookup() {
            return new PermissionLookup(null, RestClient.create("http://unused.invalid"), APP);
        }

        /** The starter's route security, as its auto-configuration builds it. */
        @Bean
        RouteSecurity routeSecurity(ApplicationContext context, PermissionLookup igrpPermissionLookup) {
            List<String> publicPaths = Binder.get(context.getEnvironment())
                    .bind("igrp.security.public-paths", Bindable.listOf(String.class)).orElse(List.of());
            return new RouteSecurity(() -> {
                List<HandlerMapping> mappings = new ArrayList<>(context.getBeansOfType(HandlerMapping.class).values());
                AnnotationAwareOrderComparator.sort(mappings);
                return new RouteResolver("test-api", mappings, publicPaths);
            }, igrpPermissionLookup, new PermissionAuthenticationConverter(igrpPermissionLookup),
                    new AccessAudit(null, null));
        }

        @Bean
        UserStatusGuard userStatusGuard(IGRPUserEntityRepository userRepository) {
            return new UserStatusGuard(userRepository);
        }

        @Bean
        UserAuthorizations userAuthorizations() {
            return mock(UserAuthorizations.class);
        }

        @Bean
        SignedInGuard signedInGuard(UserAuthorizations userAuthorizations) {
            return new SignedInGuard(userAuthorizations);
        }

        @Bean
        IGRPUserEntityRepository userRepository() {
            return mock(IGRPUserEntityRepository.class);
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return mock(JwtDecoder.class);
        }

        /** Matches the production JwtAuthenticationConverter contract. */
        @Bean
        org.springframework.core.convert.converter.Converter<Jwt, ? extends AbstractAuthenticationToken>
        jwtAuthenticationConverter() {
            return jwt -> new JwtAuthenticationToken(jwt, List.of(), jwt.getSubject());
        }

        @RestController
        static class TestEndpoints {

            @GetMapping("/api/users/me/roles")
            public Map<String, String> myRoles() {
                return Map.of("ok", "true");
            }

            @PostMapping("/api/m2m/ping")
            public Map<String, String> m2mPing() {
                return Map.of("ok", "true");
            }
        }
    }
}
