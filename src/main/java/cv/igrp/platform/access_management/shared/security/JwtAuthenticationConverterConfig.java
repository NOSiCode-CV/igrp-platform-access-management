package cv.igrp.platform.access_management.shared.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

@Configuration
public class JwtAuthenticationConverterConfig {

    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationConverter defaultJwtAuthenticationConverter() {
        return new JwtAuthenticationConverter();
    }

}