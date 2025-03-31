package cv.igrp.platform.access_management;

import cv.igrp.platform.access_management.shared.config.ApplicationAuditorAware;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.LocalDateTime;
import java.util.Optional;

@SpringBootApplication
@EnableJpaAuditing(auditorAwareRef = "auditAware", dateTimeProviderRef = "auditDateTimeProvider")
public class IgrpPlatformAccessManagementApplication {

    @Bean
    public AuditorAware<String> auditAware() {
        return new ApplicationAuditorAware();
    }

    @Bean
    public DateTimeProvider auditDateTimeProvider() {
        return () -> Optional.of(LocalDateTime.now());
    }

    public static void main(String[] args) {
        SpringApplication.run(IgrpPlatformAccessManagementApplication.class, args);
    }
}