package cv.igrp.platform.access_management.shared.config;

import cv.igrp.framework.notifications.core.adapter.NotificationAdapter;
import cv.igrp.framework.notifications.mail.smtp.adapter.EmailSMTPAdapter;
import cv.igrp.framework.notifications.mail.smtp.client.LocalClient;
import cv.igrp.framework.notifications.mail.smtp.dto.SendNotificationResponseDTO;
import cv.igrp.framework.notifications.mail.smtp.model.EmailSMTPConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmailConfig {

    @Value("${spring.mail.host}")
    private String host;

    @Value("${spring.mail.port}")
    private int port;

    @Value("${spring.mail.username}")
    private String username;

    @Value("${spring.mail.password}")
    private String password;

    @Value("${spring.mail.properties.sender-id}")
    private String senderId;

    @Bean
    NotificationAdapter<SendNotificationResponseDTO> notificationAdapter(LocalClient localClient) {
        return new EmailSMTPAdapter(localClient);
    }

    @Bean
    LocalClient localClient(EmailSMTPConfig config) {
        return new LocalClient(config);
    }

    @Bean
    EmailSMTPConfig emailSMTPConfig() {
        var config = new EmailSMTPConfig();
        config.setSmtpHost(host);
        config.setSmtpUsername(username);
        config.setSmtpPassword(password);
        config.setSmtpPort(port);
        config.setSenderId(senderId);
        return config;
    }

}
