package cv.igrp.platform.access_management.shared.config;

import cv.igrp.framework.auth.core.authorization.service.AuthorizationCore;
import cv.igrp.platform.access_management.authorization.domain.service.DatabaseAuthorizeApiAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class AuthorizationConfig {

    @Bean
    @ConditionalOnMissingBean
    public AuthorizationCore authorizationCore(JdbcTemplate jdbcTemplate) {
        return new DatabaseAuthorizeApiAdapter(jdbcTemplate);
    }
}
