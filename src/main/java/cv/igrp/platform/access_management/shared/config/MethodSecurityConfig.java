package cv.igrp.platform.access_management.shared.config;

import cv.igrp.platform.access_management.shared.security.IgrpMethodSecurityExpressionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.expression.spel.support.StandardTypeLocator;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {

    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
        return new IgrpMethodSecurityExpressionHandler();
    }

    static public class IgrpTypeLocator extends StandardTypeLocator {
        public IgrpTypeLocator() {
            super();
            // Register the package of generated enums
            registerImport("cv.igrp.framework.auth.generated");
            registerImport("cv.igrp.framework.auth.generated.PermissionsRegistry");
            registerImport("cv.igrp.platform.access_management.shared.infrastructure.authorization.permission");
        }
    }

}
