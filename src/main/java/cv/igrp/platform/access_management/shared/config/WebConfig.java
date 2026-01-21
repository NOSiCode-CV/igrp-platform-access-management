package cv.igrp.platform.access_management.shared.config;

import cv.igrp.platform.access_management.shared.infrastructure.cache.CacheEvictionInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CacheEvictionInterceptor interceptor;

    public WebConfig(CacheEvictionInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns(
                        "/api/departments/**",
                        "/api/m2m/sync/**",
                        "/api/users/**"
                );
    }
}