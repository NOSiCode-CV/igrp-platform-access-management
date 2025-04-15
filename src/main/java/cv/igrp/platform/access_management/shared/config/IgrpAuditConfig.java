package cv.igrp.platform.access_management.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;

import java.time.LocalDateTime;
import java.util.Optional;

@Configuration
public class IgrpAuditConfig {

    @Bean
    public AuditorAware<String> auditAware() {
        return new ApplicationAuditorAware();
    }

    @Bean
    public DateTimeProvider auditDateTimeProvider() {
        return () -> Optional.of(LocalDateTime.now());
    }
}
