package cv.igrp.platform.access_management.shared.security.integration;

import cv.igrp.platform.access_management.shared.security.BasicAuthSecurityConfiguration;
import cv.igrp.platform.access_management.shared.security.OAuth2SecurityConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Instant;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OWASP A05 — only {@code /actuator/health} and its probe groups are public in
 * both security chains; every other actuator path needs an authenticated
 * request. The actuator endpoints themselves are stood in for by plain
 * controllers so that the chain's decision — not endpoint exposure, which
 * {@code ActuatorExposureTest} covers — is what is asserted.
 */
class ActuatorSecurityIntegrationTest {

    private static final String[] PUBLIC_PATHS = {
            "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"
    };

    private static final String[] PROTECTED_PATHS = {
            "/actuator", "/actuator/env", "/actuator/heapdump", "/actuator/resources",
            "/actuator/loggers", "/actuator/info"
    };

    @RestController
    static class FakeActuatorEndpoints {

        @GetMapping({"/actuator", "/actuator/health", "/actuator/health/liveness",
                "/actuator/health/readiness", "/actuator/env", "/actuator/heapdump",
                "/actuator/resources", "/actuator/loggers", "/actuator/info"})
        public Map<String, String> endpoint() {
            return Map.of("status", "UP");
        }
    }

    abstract static class ChainContract {

        @Autowired
        WebApplicationContext webApplicationContext;

        MockMvc mockMvc;

        @BeforeEach
        void setUp() {
            // Other tests on this thread may leave an Authentication behind; the
            // anonymous assertions below must not inherit it.
            SecurityContextHolder.clearContext();
            TestSecurityContextHolder.clearContext();
            mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                    .apply(springSecurity())
                    .build();
        }

        @Test
        void healthAndProbesArePublic() throws Exception {
            for (String path : PUBLIC_PATHS) {
                mockMvc.perform(get(path)).andExpect(status().isOk());
            }
        }

        @Test
        void otherActuatorPathsRequireAuthentication() throws Exception {
            for (String path : PROTECTED_PATHS) {
                mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
            }
        }

        @Test
        void healthPrefixDoesNotLeakToSiblingPaths() throws Exception {
            mockMvc.perform(get("/actuator/healthdump")).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @ExtendWith(SpringExtension.class)
    @WebAppConfiguration
    @ContextConfiguration(classes = {SessionEnforcementWiringIntegrationTest.TestApp.class,
            FakeActuatorEndpoints.class})
    class OAuth2Chain extends ChainContract {

        @Autowired
        JwtDecoder jwtDecoder;

        /**
         * Opted-in endpoints (e.g. {@code resources}) are reachable with an
         * operator's {@code client_credentials} token.
         */
        @Test
        void authenticatedClientReachesProtectedActuatorPath() throws Exception {
            Instant now = Instant.now();
            Jwt jwt = new Jwt("ops-cc", now, now.plusSeconds(300), Map.of("alg", "RS256"),
                    Map.of("sub", "igrp-ops", "iss", "http://localhost", "jti", "ops-jti"));
            when(jwtDecoder.decode("ops-cc")).thenReturn(jwt);

            mockMvc.perform(get("/actuator/resources").header("Authorization", "Bearer ops-cc"))
                    .andExpect(status().isOk());
        }
    }

    @Configuration
    @EnableWebMvc
    @Import({BasicAuthSecurityConfiguration.class, FakeActuatorEndpoints.class})
    static class BasicAuthApp {
    }

    @Nested
    @ExtendWith(SpringExtension.class)
    @WebAppConfiguration
    @ContextConfiguration(classes = BasicAuthApp.class)
    @ActiveProfiles("basic-auth")
    class BasicAuthChain extends ChainContract {
    }
}
