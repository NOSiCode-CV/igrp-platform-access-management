package cv.igrp.platform.access_management.compat;

import cv.igrp.platform.filemanager.StorageService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Puts {@link InMemoryStorageService} in front of the configured storage provider. */
@TestConfiguration(proxyBeanMethods = false)
class CompatStorageConfiguration {

    @Bean
    @Primary
    StorageService compatStorageService() {
        return new InMemoryStorageService();
    }
}
