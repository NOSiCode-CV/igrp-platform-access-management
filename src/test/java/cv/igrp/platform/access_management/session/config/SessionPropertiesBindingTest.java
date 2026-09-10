package cv.igrp.platform.access_management.session.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guards the contract operators rely on: setting IGRP_SESSION_* actually changes
 * behaviour. SessionProperties previously exposed getters only, and Spring's
 * JavaBean binder needs setters, so every override was silently ignored and the
 * hard-coded defaults always won.
 */
class SessionPropertiesBindingTest {

    @Test
    void propertyOverrides_areBound() {
        SessionProperties p = new Binder(new MapConfigurationPropertySource(Map.of(
                "igrp.session.timeout-seconds", "5400",
                "igrp.session.absolute-timeout-seconds", "36000",
                "igrp.session.max-per-user", "3",
                "igrp.session.heartbeat-debounce-seconds", "15",
                "igrp.session.refresh-grace-seconds", "120")))
                .bind("igrp.session", SessionProperties.class)
                .orElseGet(SessionProperties::new);

        assertEquals(5400L, p.getTimeoutSeconds());
        assertEquals(36000L, p.getAbsoluteTimeoutSeconds());
        assertEquals(3, p.getMaxPerUser());
        assertEquals(15L, p.getHeartbeatDebounceSeconds());
        assertEquals(120L, p.getRefreshGraceSeconds());
    }

    @Test
    void environmentVariableForm_isBound() {
        // The exact form deployments use (e.g. IGRP_SESSION_TIMEOUT_SECONDS in .env).
        SystemEnvironmentPropertySource env = new SystemEnvironmentPropertySource(
                "env", Map.of("IGRP_SESSION_TIMEOUT_SECONDS", "5400"));

        SessionProperties p = new Binder(ConfigurationPropertySources.from(env))
                .bind("igrp.session", SessionProperties.class)
                .orElseGet(SessionProperties::new);

        assertEquals(5400L, p.getTimeoutSeconds());
    }

    @Test
    void defaults_whenNothingIsSet() {
        SessionProperties p = new SessionProperties();

        assertEquals(1800L, p.getTimeoutSeconds());
        assertEquals(28800L, p.getAbsoluteTimeoutSeconds());
        assertEquals(300L, p.getRefreshGraceSeconds());
    }
}
