package cv.igrp.platform.access_management.permission_fetch.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** v3-06 (#14): binds {@link PermissionSetProperties}. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PermissionSetProperties.class)
public class PermissionFetchConfig {
}
