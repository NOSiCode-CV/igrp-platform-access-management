package cv.igrp.platform.access_management;

import cv.igrp.platform.access_management.shared.infrastructure.service.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(
  exclude = WebFluxAutoConfiguration.class
)
@EnableAsync
@ComponentScan(basePackages = "cv.igrp")
public class IgrpPlatformAccessManagementApplication {

    Logger logger = LoggerFactory.getLogger(IgrpPlatformAccessManagementApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(IgrpPlatformAccessManagementApplication.class, args);
    }

    @Bean
    public CommandLineRunner initialConfiguration(ConfigurationService service) {
        try {
            return args -> service.initializeSystemConfiguration();
        } catch (Exception e) {
            logger.warn("[Startup Config] Failure in initial configuration check", e);
            return args -> {};
        }
    }

    @Bean
    public CommandLineRunner redisCheck(RedisConnectionFactory connectionFactory) {
        return args -> {
            try {
                connectionFactory.getConnection().ping();
                logger.info("✅ Redis connection successful!");
            } catch (Exception e) {
                logger.error("❌ Redis connection failed!", e);
            }
        };
    }

}
