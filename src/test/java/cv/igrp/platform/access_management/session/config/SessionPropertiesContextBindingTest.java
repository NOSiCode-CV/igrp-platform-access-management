package cv.igrp.platform.access_management.session.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Same binding path as the running application (ConfigurationPropertiesBindingPostProcessor),
 * not just a bare Binder. Proves both that overrides are applied and that the context starts.
 */
class SessionPropertiesContextBindingTest {

    @EnableConfigurationProperties(SessionProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class);

    @Test
    void contextStarts_withTheValuesApplicationPropertiesAlwaysSets() {
        // application.properties always sets absolute-timeout-seconds (defaulted via ${...:28800}).
        runner.withPropertyValues("igrp.session.absolute-timeout-seconds=28800")
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    void contextStarts_andAppliesIdleTimeoutOverride() {
        runner.withPropertyValues(
                        "igrp.session.absolute-timeout-seconds=28800",
                        "igrp.session.timeout-seconds=5400")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.getBean(SessionProperties.class).getTimeoutSeconds()).isEqualTo(5400L);
                });
    }
}
