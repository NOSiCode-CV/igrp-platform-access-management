package cv.igrp.platform.access_management.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Without SPRING_PROFILES_ACTIVE the service starts on the development profile,
 * never on the bare default profile (which cannot authenticate users).
 * Only the configuration files are loaded here, not the application.
 */
class DefaultProfileTest {

    @Configuration(proxyBeanMethods = false)
    static class Empty {
    }

    @Test
    @DisplayName("No active profile → development applies")
    void defaultsToDevelopment() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(Empty.class)
                .web(WebApplicationType.NONE)
                .run()) {
            assertArrayEquals(new String[0], context.getEnvironment().getActiveProfiles());
            assertArrayEquals(new String[]{"development"}, context.getEnvironment().getDefaultProfiles());
            assertEquals("update", context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"));
        }
    }

    @Test
    @DisplayName("An explicit profile wins over the default")
    void explicitProfileWins() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(Empty.class)
                .web(WebApplicationType.NONE)
                .run("--spring.profiles.active=production")) {
            assertArrayEquals(new String[]{"production"}, context.getEnvironment().getActiveProfiles());
            assertEquals("validate", context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"));
        }
    }
}
