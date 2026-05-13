package cv.igrp.platform.access_management.shared.security.integration;

import cv.igrp.platform.access_management.session.config.SessionProperties;
import cv.igrp.platform.access_management.session.domain.service.SessionHeartbeatService;
import cv.igrp.platform.access_management.session.infrastructure.metrics.SessionMetrics;
import cv.igrp.platform.access_management.session.infrastructure.persistence.repository.SessionRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.security.M2MTokenRejectionFilter;
import cv.igrp.platform.access_management.shared.security.OAuth2SecurityConfiguration;
import cv.igrp.platform.access_management.shared.security.SessionEnforcementFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots a real Spring web application context that wires the production
 * {@link OAuth2SecurityConfiguration} together with the production
 * {@link SessionEnforcementFilter}, then drives it through MockMvc with
 * {@code apply(springSecurity())} so the full Spring Security filter chain
 * (including the {@code BearerTokenAuthenticationFilter} and the in-chain
 * {@code .addFilterAfter(SessionEnforcementFilter, ...)} registration) is
 * exercised live.
 *
 * <p>This is the test the unit-only {@code EnforcementMissingSidTest} could
 * not be: it locks down filter-chain ordering, entry-point interaction, and
 * Spring Boot's {@link FilterRegistrationBean} auto-registration trap that
 * was the root cause of two production regressions caught against
 * {@code api-demoigrp.nosi.cv}:
 * <ul>
 *   <li><b>Observation 1</b> — {@code POST /api/admin/users/{u}/sessions/{s}/kill}
 *       with a sid-less {@code client_credentials} JWT used to return 500
 *       because the filter was double-registered (once at servlet level by
 *       {@code @Component} auto-registration, once in the security chain) and
 *       the first pass marked the request as already-filtered, neutering the
 *       second pass.
 *   <li><b>Observation 2</b> — {@code GET /api/users/me} with the same token
 *       used to return 401 but with no {@code WWW-Authenticate} header at all,
 *       because the filter never ran and some downstream entry point produced
 *       a bare 401.
 * </ul>
 *
 * <p>The fix is in two places: a {@code FilterRegistrationBean} declared by
 * {@code OAuth2SecurityConfiguration} that sets the auto-registration to
 * {@code enabled=false}, and {@code unauthorized()} now commits the response
 * via {@code flushBuffer()} so no downstream filter can rewrite headers.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = SessionEnforcementWiringIntegrationTest.TestApp.class)
@TestPropertySource(properties = {
        "igrp.session.enforcement-enabled=true",
        "igrp.access.m2m.sync-token=test-m2m-token"
})
class SessionEnforcementWiringIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JwtDecoder jwtDecoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    /**
     * Locks down the fix for Observation 1's root cause: a Spring Boot
     * {@link FilterRegistrationBean} for the filter exists and has
     * {@code setEnabled(false)}, so the {@code @Component}-annotated
     * {@code SessionEnforcementFilter} cannot get auto-wired at the embedded
     * servlet level (where {@code SecurityContextHolder} is empty).
     */
    @Test
    @SuppressWarnings("rawtypes")
    void filterIsNotAutoRegisteredAtServletLevel() {
        Map<String, FilterRegistrationBean> registrations =
                webApplicationContext.getBeansOfType(FilterRegistrationBean.class);

        FilterRegistrationBean<?> match = registrations.values().stream()
                .filter(reg -> reg.getFilter() instanceof SessionEnforcementFilter)
                .findFirst()
                .orElse(null);

        assertNotNull(match, "SessionEnforcementFilter must have an explicit "
                + "FilterRegistrationBean to opt out of Spring Boot auto-registration "
                + "as a global servlet filter — otherwise it would short-circuit "
                + "OncePerRequestFilter before the security chain populates the "
                + "SecurityContext.");
        assertFalse(match.isEnabled(),
                "FilterRegistrationBean<SessionEnforcementFilter>.setEnabled(true) "
                        + "would re-introduce the production bug where /api/admin/** "
                        + "admitted sid-less JWTs.");
    }

    /**
     * Observation 2 / T-C3 — sid-less JWT on a generic enforced path must
     * yield 401 with a {@code WWW-Authenticate} header that survives all
     * downstream filters.
     */
    @Test
    void usersMeWithSidlessTokenReturns401WithMissingSidChallenge() throws Exception {
        when(jwtDecoder.decode("test-no-sid")).thenReturn(sidlessJwt("test-no-sid"));

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer test-no-sid"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"))
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer ")))
                .andExpect(header().string("WWW-Authenticate", containsString("error=\"invalid_token\"")))
                .andExpect(header().string("WWW-Authenticate", containsString("error_description=\"missing_sid\"")))
                .andExpect(content().string(containsString("missing_sid")));
    }

    /**
     * Observation 1 / T-C3 — the same sid-less token must NOT be able to reach
     * the admin controller via {@code /api/admin/users/{u}/sessions/{s}/kill}.
     * A leaked-past-filter request would surface here as 500 because the test
     * controller deliberately blows up.
     */
    @Test
    void adminKillSessionWithSidlessTokenReturns401NotInternalError() throws Exception {
        when(jwtDecoder.decode("test-no-sid")).thenReturn(sidlessJwt("test-no-sid"));

        mockMvc.perform(post("/api/admin/users/3/sessions/{sid}/kill",
                                "00000000-0000-0000-0000-000000000000")
                        .header("Authorization", "Bearer test-no-sid")
                        .contentType("application/json")
                        .content("{\"reason\":\"x\",\"killedBy\":\"admin\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate",
                        containsString("error_description=\"missing_sid\"")));
    }

    /**
     * T-C4 / FR-11 — M2M chain stays on its own URL prefix and is unaffected
     * by the enforcement filter.
     */
    @Test
    void m2mPathWithStaticTokenIsNotAffectedByEnforcementFilter() throws Exception {
        mockMvc.perform(post("/api/m2m/ping")
                        .header("X-Machine-Service-ID", "test-svc")
                        .header("X-Machine-Auth-Token", "test-m2m-token")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void m2mPathWithWrongTokenIsRejectedByM2MChainNotEnforcementFilter() throws Exception {
        mockMvc.perform(post("/api/m2m/ping")
                        .header("X-Machine-Service-ID", "test-svc")
                        .header("X-Machine-Auth-Token", "wrong-token")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                // M2M chain produces a plain-text body, not the enforcement filter's
                // missing_sid challenge — proves the request went through the M2M
                // chain, not the OAuth2 resource server chain.
                .andExpect(content().string(equalTo(
                        "Unauthorized: Invalid or missing machine-to-machine authentication token.")));
    }

    private static Jwt sidlessJwt(String tokenValue) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "igrp-access-management");
        claims.put("iss", "http://localhost");
        claims.put("jti", "test-jti");
        // Intentionally no "sid" — simulating a client_credentials issuance.
        Instant now = Instant.now();
        return new Jwt(tokenValue, now, now.plusSeconds(300),
                Map.of("alg", "RS256"),
                claims);
    }

    // --- Minimal Spring web context -----------------------------------------

    /**
     * Imports the production {@link OAuth2SecurityConfiguration} unchanged so
     * the actual two-chain wiring (M2M @Order(1), OAuth2 resource server
     * @Order(2)) and the {@code FilterRegistrationBean} produced by the
     * configuration are both exercised. Everything else is stubbed to avoid
     * pulling in JPA / Redis / Minio / Mail auto-config.
     */
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    @Import(OAuth2SecurityConfiguration.class)
    static class TestApp {

        @Bean
        SessionEnforcementFilter sessionEnforcementFilter(SessionRepository sessionRepository,
                                                          SessionHeartbeatService heartbeatService,
                                                          SessionProperties sessionProperties,
                                                          IGRPUserEntityRepository userRepository,
                                                          SessionMetrics sessionMetrics) {
            return new SessionEnforcementFilter(sessionRepository, heartbeatService,
                    sessionProperties, userRepository, sessionMetrics, true);
        }

        @Bean
        M2MTokenRejectionFilter m2mTokenRejectionFilter() {
            return new M2MTokenRejectionFilter();
        }

        @Bean
        SessionProperties sessionProperties() {
            return new SessionProperties();
        }

        @Bean
        SessionRepository sessionRepository() {
            return mock(SessionRepository.class);
        }

        @Bean
        SessionHeartbeatService sessionHeartbeatService() {
            return mock(SessionHeartbeatService.class);
        }

        @Bean
        SessionMetrics sessionMetrics() {
            return mock(SessionMetrics.class);
        }

        @Bean
        IGRPUserEntityRepository userRepository() {
            return mock(IGRPUserEntityRepository.class);
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return mock(JwtDecoder.class);
        }

        /**
         * Match the production JwtAuthenticationConverter contract.
         */
        @Bean
        org.springframework.core.convert.converter.Converter<Jwt, ? extends AbstractAuthenticationToken>
        jwtAuthenticationConverter() {
            return jwt -> new JwtAuthenticationToken(jwt, List.of(), jwt.getSubject());
        }

        @RestController
        static class TestEndpoints {

            @GetMapping("/api/users/me")
            public Map<String, String> me() {
                return Map.of("status", "leaked-past-filter");
            }

            @PostMapping("/api/admin/users/{userId}/sessions/{sessionId}/kill")
            public Map<String, String> kill(@PathVariable String userId,
                                            @PathVariable String sessionId,
                                            @RequestBody(required = false) Map<String, Object> body) {
                throw new IllegalStateException(
                        "Admin controller must not run for sid-less tokens");
            }

            @PostMapping("/api/m2m/ping")
            public Map<String, String> m2mPing() {
                return Map.of("ok", "true");
            }
        }
    }
}
