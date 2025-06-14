package cv.igrp.platform.access_management;

import cv.igrp.platform.access_management.shared.infrastructure.service.ConfigurationService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;


@SpringBootApplication
@EnableJpaAuditing(dateTimeProviderRef = "auditDateTimeProvider", auditorAwareRef = "applicationAuditorAware")
@ComponentScan(basePackages = "cv.igrp")
public class IgrpPlatformAccessManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(IgrpPlatformAccessManagementApplication.class, args);
    }

    @Bean
    public CommandLineRunner initialConfiguration(ConfigurationService service) {
        return args -> service.createSuperAdminUser();
    }

}