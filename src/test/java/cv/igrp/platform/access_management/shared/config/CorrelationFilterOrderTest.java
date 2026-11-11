package cv.igrp.platform.access_management.shared.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.webmvc.v6_0.SpringWebMvcTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.core.Ordered;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v3-24 (#31): the correlation filter reads the trace id, so it must run after
 * the OpenTelemetry server filter that starts the span, and before Spring
 * Security, so security and audit logging already have the id.
 */
class CorrelationFilterOrderTest {

    @Test
    void runsAfterTheOtelServerFilterAndBeforeSpringSecurity() {
        Ordered otelServerFilter = (Ordered) SpringWebMvcTelemetry.create(OpenTelemetry.noop()).createServletFilter();

        assertThat(CorrelationFilterConfig.ORDER)
                .isGreaterThan(otelServerFilter.getOrder())
                .isLessThan(SecurityFilterProperties.DEFAULT_FILTER_ORDER);
    }
}
