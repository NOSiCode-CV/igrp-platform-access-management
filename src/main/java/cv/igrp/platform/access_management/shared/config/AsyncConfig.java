package cv.igrp.platform.access_management.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "igrpTaskExecutor")
    public Executor singleThreadExecutor() {
        return Executors.newSingleThreadExecutor();
    }
}
