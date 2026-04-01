package cv.igrp.platform.access_management.shared.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

@Configuration
@Profile("!basic-auth") // Ignore this config when 'basic-auth' is active
public class JwtAuthenticationConverterConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationConverterConfig.class);

    @Bean
    @Primary
    @ConditionalOnMissingBean
    public Converter<Jwt, ? extends AbstractAuthenticationToken> customJwtAuthenticationConverter() {
        log.info(" Creating CustomJwtAuthenticationConverter bean");
        return new CustomJwtAuthenticationConverter();
    }
}