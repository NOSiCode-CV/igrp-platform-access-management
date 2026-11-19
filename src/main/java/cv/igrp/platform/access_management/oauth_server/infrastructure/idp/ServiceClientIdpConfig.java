package cv.igrp.platform.access_management.oauth_server.infrastructure.idp;

import cv.igrp.framework.auth.autentika.adapter.AutentikaAdapter;
import cv.igrp.framework.auth.autentika.config.AutentikaConfiguration;
import cv.igrp.framework.auth.autentika.config.AutentikaSpringProperties;
import cv.igrp.framework.auth.core.adapter.IAdapter;
import cv.igrp.framework.auth.keycloak.adapter.KeycloakAdapter;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Which IdP service clients live in (v3-36, #44): {@code igrp.oauth.service-clients.idp}
 * ({@code IGRP_OAUTH_SERVICE_CLIENTS_IDP}), {@code keycloak} by default or {@code autentika}.
 *
 * <p>The setting alone picks the adapter. With {@code autentika}, AM declares the Autentika
 * adapter itself, so both libraries' auto-configurations back off (they only add an
 * {@link IAdapter} when none exists). With {@code keycloak}, the Keycloak auto-configuration
 * adds its adapter as before. At startup AM refuses an adapter that does not match the
 * setting (for example {@code igrp.autentika.enabled=true} next to {@code keycloak}) and an
 * unknown IdP, so a client is never created in an IdP other than the one stored on its row.
 */
@Configuration(proxyBeanMethods = false)
public class ServiceClientIdpConfig {

    static final String KEYCLOAK = "keycloak";
    static final String AUTENTIKA = "autentika";
    static final String PROPERTY = "igrp.oauth.service-clients.idp";

    @Bean
    SmartInitializingSingleton serviceClientIdpCheck(IAdapter adapter,
                                                     @Value("${" + PROPERTY + ":" + KEYCLOAK + "}") String idp) {
        return () -> requireMatchingAdapter(idp, adapter);
    }

    /** Refuses to start when the adapter is not the one the setting names. */
    static void requireMatchingAdapter(String idp, IAdapter adapter) {
        Class<? extends IAdapter> expected = switch (idp == null ? "" : idp.trim().toLowerCase(Locale.ROOT)) {
            case KEYCLOAK -> KeycloakAdapter.class;
            case AUTENTIKA -> AutentikaAdapter.class;
            default -> throw new IllegalStateException(PROPERTY + " must be '" + KEYCLOAK + "' or '"
                    + AUTENTIKA + "', not '" + idp + "'");
        };
        if (!expected.isInstance(adapter)) {
            throw new IllegalStateException(PROPERTY + "=" + idp + " needs the " + expected.getSimpleName()
                    + ", but the IdP adapter is " + adapter.getClass().getName()
                    + ". Remove igrp.autentika.enabled, or set " + PROPERTY + " to match.");
        }
    }

    /** The Autentika adapter, from {@code IGRP_AUTENTIKA_*} ({@code igrp.autentika.*}). */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = PROPERTY, havingValue = AUTENTIKA)
    @EnableConfigurationProperties(AutentikaSpringProperties.class)
    static class Autentika {

        @Bean
        IAdapter autentikaServiceClientAdapter(AutentikaSpringProperties properties) {
            return new AutentikaAdapter(configuration(properties));
        }

        /** The gateway settings, or a startup failure naming every missing variable. */
        static AutentikaConfiguration configuration(AutentikaSpringProperties properties) {
            List<String> missing = new ArrayList<>();
            if (properties.registrationEndpoint() == null) {
                missing.add("IGRP_AUTENTIKA_REGISTRATION_ENDPOINT");
            }
            if (properties.tokenEndpoint() == null) {
                missing.add("IGRP_AUTENTIKA_TOKEN_ENDPOINT");
            }
            if (isBlank(properties.consumerKey())) {
                missing.add("IGRP_AUTENTIKA_CONSUMER_KEY");
            }
            if (isBlank(properties.consumerSecret())) {
                missing.add("IGRP_AUTENTIKA_CONSUMER_SECRET");
            }
            if (!missing.isEmpty()) {
                throw new IllegalStateException(PROPERTY + "=" + AUTENTIKA + " needs " + String.join(", ", missing));
            }
            return AutentikaConfiguration.withClientCredentials(properties.registrationEndpoint(),
                    properties.tokenEndpoint(), properties.consumerKey(), properties.consumerSecret(),
                    properties.requestTimeout());
        }

        private static boolean isBlank(String value) {
            return value == null || value.isBlank();
        }
    }
}
