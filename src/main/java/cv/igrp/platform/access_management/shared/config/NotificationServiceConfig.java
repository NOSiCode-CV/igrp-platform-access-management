package cv.igrp.platform.access_management.shared.config;

import cv.igrp.platform.notification.client.ApiClient;
import cv.igrp.platform.notification.client.api.NotificationsApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring wiring for the Notification Service Java client. Both the
 * {@link ApiClient} and the {@link NotificationsApi} beans are created only
 * when {@code igrp.notification.service.enabled=true} — a
 * {@link ConditionalOnProperty} switch that also lets deployments flip the
 * whole primary path off without redeploying, forcing the invitation flow
 * to fall back to the legacy Spring Mail sender.
 *
 * <p>{@link cv.igrp.platform.access_management.notification.domain.service.InvitationNotificationSender}
 * consumes {@link NotificationsApi} through an {@code Optional} injection so
 * the compile-time dependency on this bean is soft: with the switch off, the
 * sender sees {@code Optional.empty()} and immediately delegates to the
 * fallback path.
 */
@Configuration
@EnableConfigurationProperties(NotificationServiceProperties.class)
@ConditionalOnProperty(name = "igrp.notification.service.enabled", havingValue = "true")
public class NotificationServiceConfig {

    /**
     * The shared {@link ApiClient} instance. Base URL points at the
     * Notification Service; the OAuth2 authorization server URL is passed
     * separately to {@code setClientCredentials(...)} because Access
     * Management (the OAuth2 authority) commonly lives on a different host
     * from the Notification Service in production.
     */
    @Bean
    public ApiClient notificationServiceApiClient(NotificationServiceProperties properties) {
        ApiClient client = new ApiClient();
        client.setBaseUrl(properties.getBaseUrl());
        String scope = properties.getScope();
        if (scope != null && !scope.isBlank()) {
            client.setClientCredentials(
                    properties.getAuthUrl(),
                    properties.getClientId(),
                    properties.getClientSecret(),
                    scope);
        } else {
            client.setClientCredentials(
                    properties.getClientId(),
                    properties.getClientSecret());
        }
        return client;
    }

    /**
     * Facade over {@link ApiClient} scoped to notification-send calls. Thread-safe
     * and cheap to hold as a singleton; the token is refreshed transparently by
     * the underlying {@code ClientCredentialsTokenProvider} configured above.
     */
    @Bean
    public NotificationsApi notificationsApi(ApiClient notificationServiceApiClient) {
        return new NotificationsApi(notificationServiceApiClient);
    }
}
