package cv.igrp.platform.access_management.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditDateTimeProvider", auditorAwareRef = "applicationAuditorAware")
public class JpaConfig {}