package cv.igrp.platform.access_management.shared.security;

import cv.igrp.framework.auth.core.route.PermissionLookup;
import cv.igrp.platform.access_management.permission_fetch.domain.service.PermissionFetchService;
import cv.igrp.platform.access_management.permission_fetch.infrastructure.InProcessPermissionFetch;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * AM on the 0.3 starter's route security (#36). The starter's permission
 * lookup asks AM over HTTP on a cache miss; in AM it asks in-process instead
 * ({@link InProcessPermissionFetch}), as decided in #8.
 */
@Configuration(proxyBeanMethods = false)
public class AmRouteSecurityConfig {

    private AmRouteSecurityConfig() {
        // Only static infrastructure beans.
    }

    /** The starter's lookup bean, whose client is replaced. */
    static final String LOOKUP_BEAN = "igrpPermissionLookup";

    /**
     * Rebuilds the starter's lookup with the in-process client, keeping its
     * Redis reads and its decisions. Static and lazy, so no bean is created
     * early for it.
     */
    @Bean
    static InProcessLookup amInProcessPermissionLookup(ObjectProvider<PermissionFetchService> fetches,
                                                       ObjectProvider<JwtDecoder> decoders,
                                                       ObjectProvider<StringRedisTemplate> redis,
                                                       ObjectProvider<PermissionLookup> lookups,
                                                       Environment environment) {
        return new InProcessLookup(fetches, decoders, redis, lookups, environment);
    }

    /**
     * Replaces the starter's lookup, then checks it did: without the in-process
     * client AM would call itself over HTTP at {@code igrp.access.api.base-url},
     * so it refuses to start instead, should the starter ever rename its bean.
     */
    static final class InProcessLookup implements BeanPostProcessor, SmartInitializingSingleton {

        private final ObjectProvider<PermissionFetchService> fetches;
        private final ObjectProvider<JwtDecoder> decoders;
        private final ObjectProvider<StringRedisTemplate> redis;
        private final ObjectProvider<PermissionLookup> lookups;
        private final Environment environment;
        private boolean replaced;

        InProcessLookup(ObjectProvider<PermissionFetchService> fetches, ObjectProvider<JwtDecoder> decoders,
                        ObjectProvider<StringRedisTemplate> redis, ObjectProvider<PermissionLookup> lookups,
                        Environment environment) {
            this.fetches = fetches;
            this.decoders = decoders;
            this.redis = redis;
            this.lookups = lookups;
            this.environment = environment;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (!(bean instanceof PermissionLookup) || !LOOKUP_BEAN.equals(beanName)) {
                return bean;
            }
            replaced = true;
            return new PermissionLookup(redis.getIfAvailable(),
                    new InProcessPermissionFetch(fetches, decoders).client(),
                    environment.getRequiredProperty("igrp.security.application-code"));
        }

        @Override
        public void afterSingletonsInstantiated() {
            if (lookups.getIfAvailable() != null && !replaced) {
                throw new IllegalStateException("The starter's permission lookup is not the '" + LOOKUP_BEAN
                        + "' bean AM expects, so AM would ask itself over HTTP on a cache miss; "
                        + "update AmRouteSecurityConfig for this starter version.");
            }
        }
    }
}
