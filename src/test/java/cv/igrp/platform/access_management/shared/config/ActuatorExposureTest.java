package cv.igrp.platform.access_management.shared.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.actuate.autoconfigure.availability.AvailabilityHealthContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.availability.AvailabilityProbesAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.context.properties.ConfigurationPropertiesReportEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.env.EnvironmentEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.logging.LoggersEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.management.HeapDumpWebEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.management.ThreadDumpEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementContextAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.servlet.ServletManagementContextAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.availability.ApplicationAvailabilityAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OWASP A05 — locks down which actuator endpoints the staging and production
 * profiles publish, using the real {@code application-<profile>.properties}
 * files and the real actuator web endpoints (security is covered separately by
 * {@code ActuatorSecurityIntegrationTest}).
 *
 * <p>Only the actuator + MVC auto-configurations are imported, so the test
 * needs no database, Redis or IdP.
 */
class ActuatorExposureTest {

    @SpringBootConfiguration
    @ImportAutoConfiguration({
            DispatcherServletAutoConfiguration.class,
            WebMvcAutoConfiguration.class,
            HttpMessageConvertersAutoConfiguration.class,
            JacksonAutoConfiguration.class,
            ApplicationAvailabilityAutoConfiguration.class,
            EndpointAutoConfiguration.class,
            WebEndpointAutoConfiguration.class,
            ManagementContextAutoConfiguration.class,
            ServletManagementContextAutoConfiguration.class,
            HealthContributorAutoConfiguration.class,
            HealthEndpointAutoConfiguration.class,
            AvailabilityHealthContributorAutoConfiguration.class,
            AvailabilityProbesAutoConfiguration.class,
            EnvironmentEndpointAutoConfiguration.class,
            ConfigurationPropertiesReportEndpointAutoConfiguration.class,
            HeapDumpWebEndpointAutoConfiguration.class,
            ThreadDumpEndpointAutoConfiguration.class,
            LoggersEndpointAutoConfiguration.class
    })
    @Import(ResourceConfig.class)
    static class ActuatorOnlyApp {
    }

    abstract static class DefaultExposure {

        @Autowired
        WebApplicationContext context;

        MockMvc mockMvc;

        @BeforeEach
        void setUp() {
            mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        }

        @Test
        void healthReportsStatusWithoutDetails() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"))
                    .andExpect(jsonPath("$.components").doesNotExist())
                    .andExpect(jsonPath("$.details").doesNotExist());
        }

        @Test
        void livenessAndReadinessProbesAreServed() throws Exception {
            mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
            mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        }

        @Test
        void sensitiveEndpointsAreNotPublished() throws Exception {
            for (String endpoint : new String[]{"env", "configprops", "heapdump", "threaddump",
                    "loggers", "resources"}) {
                mockMvc.perform(get("/actuator/" + endpoint)).andExpect(status().isNotFound());
            }
        }

        @Test
        void discoveryPageListsOnlyHealth() throws Exception {
            mockMvc.perform(get("/actuator"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("/actuator/env"))))
                    .andExpect(content().string(not(containsString("/actuator/heapdump"))))
                    .andExpect(content().string(not(containsString("/actuator/resources"))));
        }
    }

    @Nested
    @SpringBootTest(classes = ActuatorOnlyApp.class)
    @ActiveProfiles("production")
    class Production extends DefaultExposure {
    }

    @Nested
    @SpringBootTest(classes = ActuatorOnlyApp.class)
    @ActiveProfiles("staging")
    class Staging extends DefaultExposure {
    }

    /**
     * An operator widening the include list to '*' still cannot reach the
     * secret-leaking endpoints (exclude list) nor change anything (read-only cap).
     */
    @Nested
    @SpringBootTest(classes = ActuatorOnlyApp.class)
    @ActiveProfiles("production")
    @TestPropertySource(properties = "MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=*")
    class WildcardOverride {

        @Autowired
        WebApplicationContext context;

        MockMvc mockMvc;

        @BeforeEach
        void setUp() {
            mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        }

        @Test
        void excludedEndpointsStayHidden() throws Exception {
            for (String endpoint : new String[]{"env", "configprops", "heapdump", "threaddump"}) {
                mockMvc.perform(get("/actuator/" + endpoint)).andExpect(status().isNotFound());
            }
        }

        @Test
        void optedInEndpointsAreReadOnly() throws Exception {
            mockMvc.perform(get("/actuator/resources")).andExpect(status().isOk());
            mockMvc.perform(get("/actuator/loggers/ROOT")).andExpect(status().isOk());
            mockMvc.perform(post("/actuator/loggers/ROOT")
                            .contentType("application/json")
                            .content("{\"configuredLevel\":\"TRACE\"}"))
                    .andExpect(status().is4xxClientError());
        }
    }
}
