package cv.igrp.platform.access_management.notification.domain.service;

import cv.igrp.framework.notifications.core.adapter.NotificationAdapter;
import cv.igrp.framework.notifications.core.model.Notification;
import cv.igrp.framework.notifications.core.model.NotificationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * A no-operation (no-op) implementation of NotificationAdapter.
 * Used when notifications are disabled (igrp.notifications.enabled != mail).
 */
@SuppressWarnings("unused")
public class NoOpNotificationAdapter implements NotificationAdapter<NotificationResult> {

    private Logger LOGGER = LoggerFactory.getLogger(NoOpNotificationAdapter.class);

    @Override
    public NotificationResult send(Notification notification) {
        LOGGER.warn("Notifications are disabled. No notification sent");
        NotificationResult result = new NotificationResult();
        result.setSuccess(false);
        result.setMessage("Notifications are disabled. No notification sent");
        result.setTimestamp(Instant.now());
        return result;
    }
}
