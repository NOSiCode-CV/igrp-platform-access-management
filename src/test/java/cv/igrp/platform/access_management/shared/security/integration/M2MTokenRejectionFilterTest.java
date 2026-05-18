package cv.igrp.platform.access_management.shared.security.integration;

import cv.igrp.platform.access_management.session.config.SessionProperties;
import cv.igrp.platform.access_management.session.domain.service.SessionHeartbeatService;
import cv.igrp.platform.access_management.session.infrastructure.metrics.SessionMetrics;
import cv.igrp.platform.access_management.session.infrastructure.persistence.repository.SessionRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.security.M2MTokenRejectionFilter;
import cv.igrp.platform.access_management.shared.security.OAuth2SecurityConfiguration;
import cv.igrp.platform.access_management.shared.security.SessionEnforcementFilter;
import cv.igrp.platform.access_management.shared.security.UserStatusGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
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
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase G1 / FR-13 — boots the production {@link OAuth2SecurityConfiguration}
 * chain end-to-end through MockMvc and locks in the M2M token rejection
 * contract that prevents the live production crash reproduced against
 * {@code https://api-demoigrp.nosi.cv/igrp-access-management}:
 * <pre>
 *   POST /api/admin/users/3/sessions/{uuid}/kill
 *   Authorization: Bearer &lt;client_credentials JWT, sub == client_id&gt;
 * </pre>
 * Before G1: 500 {@link NumberFormatException} at
 * {@code PermissionCacheService:152}. After G1: 401 with
 * {@code WWW-Authenticate: Bearer error="invalid_token", error_description="m2m_token_not_allowed"}.
 *
 * <p>The test also asserts the M2M token still works on {@code /api/m2m/**}
 * (different security chain, filter not registered) and a real user token
 * (sid present) passes through the M2MTokenRejectionFilter unaffected.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = M2MTokenRejectionFilterTest.TestApp.class)
@TestPropertySource(properties = {
        "igrp.session.enforcement-enabled=true"
})
class M2MTokenRejectionFilterTest {

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

    @Test
    void m2mTokenOnAdminPathReturns401WithM2MNotAllowedChallenge() throws Exception {
        when(jwtDecoder.decode("test-cc")).thenReturn(ccJwt("test-cc"));

        mockMvc.perform(post("/api/admin/users/3/sessions/{sid}/kill",
                                "00000000-0000-0000-0000-000000000000")
                        .header("Authorization", "Bearer test-cc")
                        .contentType("application/json")
                        .content("{\"reason\":\"x\",\"killedBy\":\"admin\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"))
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer ")))
                .andExpect(header().string("WWW-Authenticate", containsString("error=\"invalid_token\"")))
                .andExpect(header().string("WWW-Authenticate",
                        containsString("error_description=\"m2m_token_not_allowed\"")))
                .andExpect(content().string(containsString("m2m_token_not_allowed")));
    }

    @Test
    void m2mTokenOnGenericEnforcedPathReturns401WithM2MNotAllowedChallenge() throws Exception {
        when(jwtDecoder.decode("test-cc")).thenReturn(ccJwt("test-cc"));

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer test-cc"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate",
                        containsString("error_description=\"m2m_token_not_allowed\"")));
    }

    @Test
    void m2mPathAcceptsClientCredentialsJwtAndBypassesRejectionFilter() throws Exception {
        // /api/m2m/** is now served by the same OAuth2 resource-server chain as
        // user endpoints. M2MTokenRejectionFilter is included in that chain but
        // skips the /api/m2m/ prefix internally, so a canonical M2M JWT must
        // authenticate and reach the controller.
        when(jwtDecoder.decode("test-cc")).thenReturn(ccJwt("test-cc"));

        mockMvc.perform(post("/api/m2m/ping")
                        .header("Authorization", "Bearer test-cc")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void userTokenWithSidPassesThroughM2MFilter() throws Exception {
        // A real user (sid present) is admitted by M2MTokenRejectionFilter so
        // SessionEnforcementFilter sees it next. SessionEnforcementFilter rejects
        // with session_revoked because the sid does not resolve to any session
        // in the mocked repository — proving the filter passed through but the
        // session enforcement layer still ran.
        when(jwtDecoder.decode("test-user")).thenReturn(userJwt("test-user"));

        mockMvc.perform(post("/api/admin/users/3/sessions/{sid}/kill",
                                "00000000-0000-0000-0000-000000000000")
                        .header("Authorization", "Bearer test-user")
                        .contentType("application/json")
                        .content("{\"reason\":\"x\",\"killedBy\":\"admin\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate",
                        containsString("error_description=\"session_revoked\"")));
    }

    private static Jwt ccJwt(String tokenValue) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "igrp-access-management");
        claims.put("client_id", "igrp-access-management");
        claims.put("iss", "http://localhost");
        claims.put("jti", "cc-jti");
        // Intentionally no "sid" — canonical client_credentials issuance.
        Instant now = Instant.now();
        return new Jwt(tokenValue, now, now.plusSeconds(300),
                Map.of("alg", "RS256"),
                claims);
    }

    private static Jwt userJwt(String tokenValue) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "3");
        claims.put("sid", "11111111-1111-1111-1111-111111111111");
        claims.put("iss", "http://localhost");
        claims.put("jti", "user-jti");
        Instant now = Instant.now();
        return new Jwt(tokenValue, now, now.plusSeconds(300),
                Map.of("alg", "RS256"),
                claims);
    }

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
        UserStatusGuard userStatusGuard(IGRPUserEntityRepository userRepository) {
            return new UserStatusGuard(userRepository);
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
                        "Admin controller must not run for M2M / sid-less tokens");
            }

            @PostMapping("/api/m2m/ping")
            public Map<String, String> m2mPing() {
                return Map.of("ok", "true");
            }
        }
    }
}
