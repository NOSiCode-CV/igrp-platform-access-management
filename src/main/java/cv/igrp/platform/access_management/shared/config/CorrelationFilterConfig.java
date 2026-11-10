package cv.igrp.platform.access_management.shared.config;

import cv.igrp.platform.access_management.shared.infrastructure.correlation.CorrelationIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * v3-24 (#31): registers {@link CorrelationIdFilter} just before Spring
 * Security's chain (-100), so security and audit logging already have the id,
 * and after the OpenTelemetry server filter, so the trace id exists.
 */
@Configuration(proxyBeanMethods = false)
public class CorrelationFilterConfig {

    static final int ORDER = -101;

    @Bean
    FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(ORDER);
        return registration;
    }
}
