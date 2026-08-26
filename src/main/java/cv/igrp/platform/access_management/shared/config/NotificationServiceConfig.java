package cv.igrp.platform.access_management.shared.config;

import cv.igrp.platform.notification.client.ApiClient;
import cv.igrp.platform.notification.client.api.NotificationsApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring wiring for the Notification Service Java client.
 *
 * <p><b>Property registration is UNCONDITIONAL.</b> {@link EnableConfigurationProperties}
 * sits on the class itself (not inside a {@link ConditionalOnProperty} block),
 * so {@link NotificationServiceProperties} is always available as a bean —
 * regardless of {@code igrp.notification.service.enabled}. Before this split,
 * flipping enabled=false skipped the whole config class → the properties bean
 * was never registered → {@code InvitationNotificationSender} could not
 * autowire it and the whole app failed to start.
 *
 * <p><b>ApiClient / NotificationsApi are CONDITIONAL.</b> Only the two beans
 * that actually make outbound calls carry {@code @ConditionalOnProperty} on
 * their factory methods, so with enabled=false neither bean is created.
 * {@code InvitationNotificationSender} consumes {@link NotificationsApi}
 * through an {@code Optional} injection, so its compile-time dependency on
 * this bean is soft: with the switch off, the sender sees
 * {@code Optional.empty()} and immediately delegates to the fallback path.
 */
@Configuration
@EnableConfigurationProperties(NotificationServiceProperties.class)
public class NotificationServiceConfig {

    /**
     * The shared {@link ApiClient} instance. Base URL points at the
     * Notification Service; the OAuth2 authorization server URL is passed
     * separately to {@code setClientCredentials(...)} because Access
     * Management (the OAuth2 authority) commonly lives on a different host
     * from the Notification Service in production.
     */
    @Bean
    @ConditionalOnProperty(name = "igrp.notification.service.enabled", havingValue = "true")
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
    @ConditionalOnProperty(name = "igrp.notification.service.enabled", havingValue = "true")
    public NotificationsApi notificationsApi(ApiClient notificationServiceApiClient) {
        return new NotificationsApi(notificationServiceApiClient);
    }
}
