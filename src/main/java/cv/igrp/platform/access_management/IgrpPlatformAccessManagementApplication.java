package cv.igrp.platform.access_management;

import cv.igrp.platform.access_management.shared.infrastructure.service.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
  exclude = WebFluxAutoConfiguration.class
)
@EnableScheduling
// The explicit scan replaces the one @SpringBootApplication declares, and with
// it Boot's TypeExcludeFilter. Restoring that filter is a no-op in production
// (no TypeExcludeFilter beans exist there); in tests it stops one test's nested
// @Configuration from leaking into every other full-context test.
@ComponentScan(basePackages = "cv.igrp",
        excludeFilters = @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class))
public class IgrpPlatformAccessManagementApplication {

    Logger logger = LoggerFactory.getLogger(IgrpPlatformAccessManagementApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(IgrpPlatformAccessManagementApplication.class, args);
    }

    @Bean
    public CommandLineRunner startupRunner(ConfigurationService configService,
                                           RedisConnectionFactory connectionFactory
    ) {
        return args -> {
            try {
                configService.initializeSystemConfiguration();
                connectionFactory.getConnection().ping();
                logger.info("✅ Redis connection successful!");
            } catch (Exception e) {
                logger.warn("[Startup] Failure in startup sequence", e);
            }
        };
    }

}
