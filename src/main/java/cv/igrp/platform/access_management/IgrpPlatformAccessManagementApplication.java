package cv.igrp.platform.access_management;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;


@SpringBootApplication
@EnableJpaAuditing(dateTimeProviderRef = "auditDateTimeProvider", auditorAwareRef = "applicationAuditorAware")
public class IgrpPlatformAccessManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(IgrpPlatformAccessManagementApplication.class, args);
    }

}