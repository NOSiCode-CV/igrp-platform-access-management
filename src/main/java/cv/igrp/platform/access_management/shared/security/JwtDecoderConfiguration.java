package cv.igrp.platform.access_management.shared.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
@Profile("!basic-auth") // Ignore this config when 'basic-auth' is active
public class JwtDecoderConfiguration {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
    private String url;

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withIssuerLocation(url).build();
    }
}
