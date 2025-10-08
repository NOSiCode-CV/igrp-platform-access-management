package cv.igrp.platform.access_management.shared.config;

import cv.igrp.framework.notifications.core.adapter.NotificationAdapter;
import cv.igrp.framework.notifications.core.model.NotificationResult;
import cv.igrp.platform.access_management.notification.domain.service.NoOpNotificationAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(
        name = "igrp.notifications.enabled",
        havingValue = "none",
        matchIfMissing = true
)
public class NotificationConfig {

    @Bean
    @ConditionalOnMissingBean(NotificationAdapter.class)
    public NotificationAdapter<NotificationResult> noopNotificationAdapter() {
        return new NoOpNotificationAdapter();
    }

}
